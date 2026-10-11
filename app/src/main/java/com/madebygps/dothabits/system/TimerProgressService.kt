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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Prototype: keeps live progress out of the cached-process freezer, without keeping the CPU awake. */
class TimerProgressService : Service() {
    private var promoted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        // Re-read under the updater lock: queued starts must not resurrect a paused or dismissed run.
        dotApp.appScope.launch { TimerNotificationUpdater.refresh(dotApp) }
        return START_NOT_STICKY
    }

    private fun promote(notification: Notification) {
        if (promoted) return
        ServiceCompat.startForeground(
            this, Notifications.ID_TIMER, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        promoted = true
        Log.i(TAG, "Foreground progress started")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        Log.i(TAG, "Foreground progress stopped")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DotHabitsTimer"
        private var instance: TimerProgressService? = null
        private var attemptedRun: TimerDismissal.Run? = null

        internal suspend fun reconcile(
            app: DotApp,
            run: TimerDismissal.Run?,
            notification: Notification?,
            retryForegroundStart: Boolean,
        ) =
            withContext(Dispatchers.Main.immediate) {
                if (notification == null || run == null) {
                    attemptedRun = null
                    instance?.let {
                        instance = null
                        ServiceCompat.stopForeground(it, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        it.stopSelf()
                    }
                    app.stopService(Intent(app, TimerProgressService::class.java))
                } else {
                    try {
                        val service = instance
                        if (service != null) {
                            service.promote(notification)
                        } else if (attemptedRun != run || retryForegroundStart) {
                            // Don't retry a denied background launch on every progress tick.
                            attemptedRun = run
                            ContextCompat.startForegroundService(app, Intent(app, TimerProgressService::class.java))
                        }
                    } catch (e: ForegroundServiceStartNotAllowedException) {
                        Log.e(TAG, "Foreground progress denied for session=${run.sessionId} generation=${run.generation}; progress remains best effort", e)
                        stopDeniedService()
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Foreground progress permission denied; progress remains best effort", e)
                        stopDeniedService()
                    }
                }
                Notifications.publishTimer(app, notification)
            }

        private fun stopDeniedService() {
            instance?.stopSelf()
            instance = null
        }
    }
}
