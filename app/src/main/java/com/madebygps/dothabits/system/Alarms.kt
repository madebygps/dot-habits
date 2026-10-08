package com.madebygps.dothabits.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.madebygps.dothabits.domain.ReminderPlanner
import com.madebygps.dothabits.domain.TimerMath
import com.madebygps.dothabits.domain.TodaySnapshot
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * AlarmManager is used only where timing matters to the user:
 *  - the next habit reminder (exact if the user allowed "Alarms & reminders", otherwise a
 *    10-minute window — Android 14+ denies SCHEDULE_EXACT_ALARM by default for new installs),
 *  - local midnight, so widgets/Glyph roll over to the new day,
 *  - the moment a running timer reaches its daily goal.
 * Everything else (steps sync, periodic widget refresh) is deferred work via WorkManager.
 */
object Alarms {
    private const val RC_REMINDER = 10
    private const val RC_MIDNIGHT = 11
    private const val RC_GOAL = 12
    private const val WINDOW_MS = 10 * 60 * 1000L

    fun canExact(context: Context) = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pi(context: Context, rc: Int, action: String, extra: Long = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context, rc,
            Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(AlarmReceiver.EXTRA_TIME, extra),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun scheduleAll(context: Context, snapshot: TodaySnapshot, zone: ZoneId = ZoneId.systemDefault()) {
        val am = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now(zone)

        // Reminder
        val next = ReminderPlanner.next(snapshot.habits.map { it.habit }, now)
        val reminderPi = pi(context, RC_REMINDER, AlarmReceiver.ACTION_REMINDER, next?.atZone(zone)?.toInstant()?.toEpochMilli() ?: 0)
        if (next == null) am.cancel(reminderPi)
        else {
            val at = next.atZone(zone).toInstant().toEpochMilli()
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, reminderPi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, reminderPi)
        }

        // Midnight rollover (+2s so "today" is unambiguously the new date)
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 2000
        val midnightPi = pi(context, RC_MIDNIGHT, AlarmReceiver.ACTION_MIDNIGHT)
        if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC, midnight, midnightPi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC, midnight, midnightPi)

        // Timer goal
        val running = snapshot.habits.firstOrNull { it.timerRunning }
        val goalAt = running?.let { TimerMath.goalReachedAt(it.value, it.habit.dailyGoalUnits, Instant.now()) }
        val goalPi = pi(context, RC_GOAL, AlarmReceiver.ACTION_TIMER_GOAL, running?.habit?.id ?: 0)
        if (goalAt == null) am.cancel(goalPi)
        else if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, goalAt.toEpochMilli(), goalPi)
        else am.setWindow(AlarmManager.RTC_WAKEUP, goalAt.toEpochMilli(), 60_000L, goalPi)
    }
}
