package com.madebygps.dothabits.system

import android.content.Context
import android.util.Log
import com.madebygps.dothabits.dotApp
import com.madebygps.dothabits.widget.DotWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fan-out after any data change. The Glyph Toy and app UI observe Room flows directly;
 * widgets, alarms and the timer notification need an explicit push.
 */
object Refresh {
    private val lock = Mutex()

    suspend fun afterDataChange(context: Context) = lock.withLock {
        val app = context.dotApp
        runCatching {
            val snapshot = app.repository.currentSnapshot()
            Notifications.updateTimer(app, snapshot)
            Alarms.scheduleAll(app, snapshot)
            DotWidget().updateAll(app)
        }.onFailure { Log.w("DotHabits", "refresh failed", it) }
        Unit
    }
}
