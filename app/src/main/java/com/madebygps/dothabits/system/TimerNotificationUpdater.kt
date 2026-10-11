package com.madebygps.dothabits.system

import android.util.Log
import com.madebygps.dothabits.DotApp
import com.madebygps.dothabits.domain.SessionState
import com.madebygps.dothabits.domain.TimerDismissal
import com.madebygps.dothabits.domain.TimerProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes fresh snapshots so an older update cannot overwrite a pause or dismissal. */
object TimerNotificationUpdater {
    private const val TAG = "DotHabitsTimer"
    private val lock = Mutex()

    suspend fun refresh(
        app: DotApp,
        allowStart: Boolean = false,
    ) = lock.withLock {
        // Read under the lock so a ticker cannot overwrite a newer pause, switch, or completion.
        val data = app.repository.raw.first()
        val snapshot = app.repository.snapshot(data)
        val running = snapshot.habits.firstOrNull { it.timerRunning }
        val run = running?.timerSessionId?.let { id ->
            running.timerGeneration?.let { TimerDismissal.Run(id, it) }
        }
        val notification = Notifications.timerNotification(app, snapshot)
        TimerProgressService.reconcile(app, run, notification, allowStart)
        if (notification != null) {
            Log.d(TAG, "Progress session=${run?.sessionId} elapsed=${running?.let { TimerProgress.from(it)?.elapsedSeconds }}")
        }
    }

    fun start(app: DotApp) {
        app.appScope.launch {
            try {
                app.repository.raw
                    .map { data ->
                        val running = data.sessions.filter { it.state == SessionState.RUNNING.name }
                        val habitIds = running.map { it.habitId }.toSet()
                        running to data.habits.filter { it.id in habitIds }
                    }
                    .distinctUntilChanged()
                    .collectLatest {
                        refresh(app, allowStart = true)
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
