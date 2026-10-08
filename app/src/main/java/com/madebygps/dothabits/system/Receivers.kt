package com.madebygps.dothabits.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.madebygps.dothabits.domain.ReminderPlanner
import com.madebygps.dothabits.dotApp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Handles reminder, midnight and end-of-session alarms. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.dotApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                val repo = app.repository
                repo.touchAlive()
                val snap = repo.currentSnapshot()
                when (intent.action) {
                    ACTION_REMINDER -> {
                        val scheduled = intent.getLongExtra(EXTRA_TIME, 0L)
                        val zone = ZoneId.systemDefault()
                        val time = if (scheduled > 0) Instant.ofEpochMilli(scheduled).atZone(zone).toLocalTime() else LocalTime.now(zone)
                        ReminderPlanner.due(snap, time).forEach { Notifications.reminder(context, it) }
                    }
                    ACTION_TIMER_GOAL -> repo.finishElapsedSessions()
                    ACTION_MIDNIGHT -> Unit
                }
                Refresh.afterDataChange(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.madebygps.dothabits.REMINDER"
        const val ACTION_MIDNIGHT = "com.madebygps.dothabits.MIDNIGHT"
        const val ACTION_TIMER_GOAL = "com.madebygps.dothabits.TIMER_GOAL"
        const val EXTRA_TIME = "time"
    }
}

/** Notification actions (pause timer). */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PAUSE_TIMER) return
        val id = intent.getLongExtra(EXTRA_HABIT_ID, -1)
        val pending = goAsync()
        context.dotApp.appScope.launch {
            try { context.dotApp.repository.pauseTimer(id) } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_PAUSE_TIMER = "com.madebygps.dothabits.PAUSE_TIMER"
        const val EXTRA_HABIT_ID = "habit_id"
    }
}

/**
 * Reboot, clock and time-zone changes. A reboot moves running timers to NEEDS_REVIEW
 * (see HabitRepository.reconcileAfterBoot); a wall-clock change re-anchors running timers to
 * their monotonic elapsed time (HabitRepository.rebaseRunningTimers); all alarms are re-registered.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        val app = context.dotApp
        app.appScope.launch {
            try {
                app.repository.reconcileAfterBoot()
                if (intent.action == Intent.ACTION_TIME_CHANGED) app.repository.rebaseRunningTimers()
                Refresh.afterDataChange(context)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
