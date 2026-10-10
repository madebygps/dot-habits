package com.madebygps.dothabits.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.madebygps.dothabits.domain.TimerDismissal
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
                if (intent.action == ACTION_TIMER_GOAL) {
                    TimerLifecycle.settle(app, intent.getLongExtra(EXTRA_SESSION_ID, -1), intent.getLongExtra(EXTRA_GENERATION, -1))
                } else TimerLifecycle.settle(app)
                val snap = repo.currentSnapshot()
                when (intent.action) {
                    ACTION_REMINDER -> {
                        val scheduled = intent.getLongExtra(EXTRA_TIME, 0L)
                        val zone = ZoneId.systemDefault()
                        val time = if (scheduled > 0) Instant.ofEpochMilli(scheduled).atZone(zone).toLocalTime() else LocalTime.now(zone)
                        ReminderPlanner.due(snap, time).forEach { Notifications.reminder(context, it) }
                    }
                    ACTION_TIMER_GOAL -> Unit
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
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_GENERATION = "generation"
    }
}

/** Notification actions (pause timer or hide this run's progress). */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISMISS_TIMER) {
            val runId = intent.getLongExtra(EXTRA_RUN_ID, -1)
            val generation = intent.getLongExtra(AlarmReceiver.EXTRA_GENERATION, -1)
            if (runId < 0 || generation < 0) Log.w("DotHabitsTimer", "Dismiss action missing timer run id or generation")
            else {
                val pending = goAsync()
                context.dotApp.appScope.launch {
                    try {
                        val running = context.dotApp.repository.currentSnapshot().habits.firstOrNull { it.timerRunning }
                        val current = running?.let { r ->
                            r.timerSessionId?.let { id -> r.timerGeneration?.let { TimerDismissal.Run(id, it) } }
                        }
                        Notifications.dismissTimer(context, TimerDismissal.Run(runId, generation), current)
                    } finally { pending.finish() }
                }
            }
            return
        }
        if (intent.action != ACTION_PAUSE_TIMER) return
        val id = intent.getLongExtra(EXTRA_HABIT_ID, -1)
        val pending = goAsync()
        context.dotApp.appScope.launch {
            try {
                context.dotApp.repository.pauseTimer(id, intent.getLongExtra(EXTRA_RUN_ID, -1),
                    intent.getLongExtra(AlarmReceiver.EXTRA_GENERATION, -1))
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_PAUSE_TIMER = "com.madebygps.dothabits.PAUSE_TIMER"
        const val EXTRA_HABIT_ID = "habit_id"
        const val ACTION_DISMISS_TIMER = "com.madebygps.dothabits.DISMISS_TIMER"
        const val EXTRA_RUN_ID = "run_id"
    }
}

/**
 * Reboot discards unfinished sessions. Clock/zone changes retain monotonic remaining time,
 * reconcile the session's original day, and re-register alarms.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        val app = context.dotApp
        app.appScope.launch {
            try {
                TimerLifecycle.settle(app)
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
