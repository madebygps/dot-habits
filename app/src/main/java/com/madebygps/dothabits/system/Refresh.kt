package com.madebygps.dothabits.system

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.madebygps.dothabits.dotApp
import com.madebygps.dothabits.widget.DotWidget
import com.madebygps.dothabits.widget.HabitWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fan-out after any data change. The Glyph Toy and app UI observe Room flows directly;
 * widgets, alarms and the timer notification need an explicit push.
 */
object Refresh {
    private const val TAG = "DotHabitsRefresh"
    private val lock = Mutex()

    suspend fun afterDataChange(context: Context, reason: String = "data-change") = lock.withLock {
        val app = context.dotApp
        val started = SystemClock.elapsedRealtime()
        var phase = "snapshot"
        runCatching {
            val phaseStarted = SystemClock.elapsedRealtime()
            val snapshot = app.repository.currentSnapshot()
            val snapshotMs = SystemClock.elapsedRealtime() - phaseStarted
            phase = "notification"
            val notificationStarted = SystemClock.elapsedRealtime()
            Notifications.updateTimer(app, snapshot)
            val notificationMs = SystemClock.elapsedRealtime() - notificationStarted
            phase = "alarms"
            val alarmsStarted = SystemClock.elapsedRealtime()
            Alarms.scheduleAll(app, snapshot)
            val alarmsMs = SystemClock.elapsedRealtime() - alarmsStarted
            phase = "dot-widget"
            val dotWidgetStarted = SystemClock.elapsedRealtime()
            DotWidget().updateAll(app)
            val dotWidgetMs = SystemClock.elapsedRealtime() - dotWidgetStarted
            phase = "habit-widget"
            val habitWidgetStarted = SystemClock.elapsedRealtime()
            HabitWidget().updateAll(app)
            val habitWidgetMs = SystemClock.elapsedRealtime() - habitWidgetStarted
            Log.i(
                TAG,
                "reason=$reason totalMs=${SystemClock.elapsedRealtime() - started} snapshotMs=$snapshotMs " +
                    "notificationMs=$notificationMs alarmsMs=$alarmsMs dotWidgetRequestMs=$dotWidgetMs " +
                    "habitWidgetRequestMs=$habitWidgetMs",
            )
        }.onFailure {
            Log.w(TAG, "reason=$reason failedPhase=$phase totalMs=${SystemClock.elapsedRealtime() - started}", it)
        }
        Unit
    }
}
