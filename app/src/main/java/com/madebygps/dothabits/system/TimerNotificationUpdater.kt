package com.madebygps.dothabits.system

import android.util.Log
import com.madebygps.dothabits.DotApp
import com.madebygps.dothabits.domain.SessionState
import com.madebygps.dothabits.domain.TimerDismissal
import com.madebygps.dothabits.domain.TimerProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A foreground service prevents caching; sleep can still delay ticks. No wake lock or polling alarm. */
object TimerNotificationUpdater {
    private const val TAG = "DotHabitsTimer"
    private const val INTERVAL_MS = 15_000L
    private val lock = Mutex()

    suspend fun refresh(app: DotApp, retryForegroundStart: Boolean = false) = lock.withLock {
        // Read under the lock so a ticker cannot overwrite a newer pause, switch, or completion.
        val data = app.repository.raw.first()
        val snapshot = app.repository.snapshot(data)
        val running = snapshot.habits.firstOrNull { it.timerRunning }
        val run = running?.timerSessionId?.let { id ->
            running.timerGeneration?.let { TimerDismissal.Run(id, it) }
        }
        val notification = Notifications.timerNotification(app, snapshot)
        TimerProgressService.reconcile(app, run, notification, retryForegroundStart)
        if (notification != null) {
            Log.d(TAG, "Progress session=${run?.sessionId} elapsed=${running?.let { TimerProgress.from(it)?.elapsedSeconds }}")
        }
    }

    fun start(app: DotApp) {
        app.appScope.launch {
            try {
                app.repository.raw
                    .map { data -> data.sessions.filter { it.state == SessionState.RUNNING.name }.map { it.id to it.generation } }
                    .distinctUntilChanged()
                    .collectLatest { runs ->
                        refresh(app)
                        if (runs.isEmpty()) return@collectLatest
                        while (true) {
                            delay(INTERVAL_MS)
                            refresh(app)
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Timer notification observer failed", e)
                throw e
            }
        }
    }
}
