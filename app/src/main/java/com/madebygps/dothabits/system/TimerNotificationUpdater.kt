package com.madebygps.dothabits.system

import android.util.Log
import com.madebygps.dothabits.DotApp
import com.madebygps.dothabits.domain.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** No service, wake lock, or polling alarm: updates are best effort while the process is alive. */
object TimerNotificationUpdater {
    private const val TAG = "DotHabitsTimer"
    private const val INTERVAL_MS = 15_000L
    private val lock = Mutex()

    suspend fun refresh(app: DotApp) = lock.withLock {
        // Read under the lock so a ticker cannot overwrite a newer pause, switch, or completion.
        val data = app.repository.raw.first()
        val snapshot = app.repository.snapshot(data)
        Notifications.updateTimer(app, snapshot)
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
