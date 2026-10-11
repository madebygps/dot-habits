package com.madebygps.dothabits.system

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.madebygps.dothabits.DotApp
import com.madebygps.dothabits.domain.TimerDismissal
import com.madebygps.dothabits.dotApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns minute-by-minute progress updates while a timer notification is visible. No wake lock. */
class TimerProgressService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updates: Job? = null
    private var promoted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        startPending = false
        if (updates?.isActive != true) {
            updates = scope.launch {
                try {
                    while (true) {
                        // A queued start must check current data before displaying progress.
                        TimerNotificationUpdater.refresh(dotApp, allowStart = false)
                        if (instance !== this@TimerProgressService) break
                        delay(60_000L)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Timer progress updates failed", e)
                    stopProgress()
                    throw e
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun promote(notification: Notification) {
        if (promoted) return
        ServiceCompat.startForeground(
            this, Notifications.ID_TIMER, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        promoted = true
        deniedRun = null
        Log.i(TAG, "Foreground progress started")
    }

    private fun stopProgress() {
        if (instance === this) instance = null
        updates?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        scope.cancel()
        Log.i(TAG, "Foreground progress stopped")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DotHabitsTimer"
        private var instance: TimerProgressService? = null
        private var startPending = false
        private var deniedRun: TimerDismissal.Run? = null

        /** Called under the notification updater's lock; service callbacks run on the main thread. */
        internal suspend fun reconcile(
            app: DotApp,
            run: TimerDismissal.Run?,
            notification: Notification?,
            allowStart: Boolean,
        ) = withContext(Dispatchers.Main.immediate) {
            val exempt = LiveProgressBattery.isExempt(app)
            var publishedNotification = notification.takeIf { exempt }
            if (notification == null || run == null || !exempt) {
                if (notification != null && run != null && !exempt) {
                    Log.w(TAG, "Live progress requires battery-optimization exemption")
                }
                startPending = false
                deniedRun = null
                instance?.stopProgress()
                app.stopService(Intent(app, TimerProgressService::class.java))
            } else {
                try {
                    val service = instance
                    if (service != null) {
                        service.promote(notification)
                    } else if (allowStart && !startPending && deniedRun != run) {
                        startPending = true
                        ContextCompat.startForegroundService(app, Intent(app, TimerProgressService::class.java))
                    }
                } catch (e: ForegroundServiceStartNotAllowedException) {
                    Log.e(TAG, "Foreground progress denied for run=$run; live progress disabled for this run", e)
                    startPending = false
                    deniedRun = run
                    instance?.stopProgress()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Foreground progress permission denied; live progress disabled for this run", e)
                    startPending = false
                    deniedRun = run
                    instance?.stopProgress()
                }
                if (deniedRun == run) publishedNotification = null
            }
            Notifications.publishTimer(app, publishedNotification)
        }
    }
}
