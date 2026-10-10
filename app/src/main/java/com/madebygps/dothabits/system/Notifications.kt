package com.madebygps.dothabits.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.madebygps.dothabits.MainActivity
import com.madebygps.dothabits.R
import com.madebygps.dothabits.domain.Habit
import com.madebygps.dothabits.domain.TimerDismissal
import com.madebygps.dothabits.domain.TimerMath
import com.madebygps.dothabits.domain.TimerProgress
import com.madebygps.dothabits.domain.TodaySnapshot

object Notifications {
    const val CH_REMINDERS = "reminders"
    const val CH_TIMER = "timer"
    const val CH_REVIEW = "timer_review"
    const val CH_SESSION_DONE = "timer_session_done"
    private const val ID_TIMER = 1
    private const val ID_REVIEW = 2
    private const val ID_GOAL = 3
    private const val REMINDER_BASE = 1000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_TIMER, context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
                NotificationChannel(CH_SESSION_DONE, context.getString(R.string.channel_session_done), NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 150, 250, 150, 400)
                },
            ),
        )
        nm.deleteNotificationChannel(CH_REVIEW)
    }

    fun canPost(context: Context) =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, habitId: Long? = null): PendingIntent =
        PendingIntent.getActivity(
            context, (habitId ?: 0L).toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                habitId?.let { putExtra(MainActivity.EXTRA_HABIT_ID, it) }
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private const val TIMER_PREFS = "timer_notifications"
    private const val DISMISSED_RUN = "dismissed_run"
    private const val DISMISSED_GENERATION = "dismissed_generation"

    private fun storedDismissal(context: Context): TimerDismissal.Run? {
        val prefs = context.getSharedPreferences(TIMER_PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(DISMISSED_GENERATION))
            TimerDismissal.Run(prefs.getLong(DISMISSED_RUN, -1L), prefs.getLong(DISMISSED_GENERATION, -1L))
        else null
    }

    /** [current] is the run now showing; a stale (older generation) callback leaves the stored pair alone. */
    fun dismissTimer(context: Context, incoming: TimerDismissal.Run, current: TimerDismissal.Run?) {
        val next = TimerDismissal.accept(current, incoming, storedDismissal(context)) ?: return
        context.getSharedPreferences(TIMER_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(DISMISSED_RUN, next.sessionId).putLong(DISMISSED_GENERATION, next.generation).apply()
    }

    /** The system owns the countdown; progress refreshes opportunistically without waking the phone. */
    @Suppress("MissingPermission")
    fun updateTimer(context: Context, snapshot: TodaySnapshot) {
        val nm = NotificationManagerCompat.from(context)
        val running = snapshot.habits.firstOrNull { it.timerRunning }
        val runId = running?.timerSessionId
        val generation = running?.timerGeneration
        val dismissed = TimerDismissal.hidden(
            storedDismissal(context),
            if (runId != null && generation != null) TimerDismissal.Run(runId, generation) else null,
        )
        if (running == null || runId == null || dismissed || !canPost(context)) {
            nm.cancel(ID_TIMER)
        } else {
            val nowMs = System.currentTimeMillis()
            val endMs = requireNotNull(running.timerEndsAt?.toEpochMilli())
            val progress = requireNotNull(TimerProgress.from(running))
            val expiresAt = minOf(endMs, snapshot.date.plusDays(1)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
            val pause = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_PAUSE_TIMER)
                    .setData(android.net.Uri.parse("dothabits://pause/$runId/${running.timerGeneration}"))
                    .putExtra(ActionReceiver.EXTRA_HABIT_ID, running.habit.id)
                    .putExtra(ActionReceiver.EXTRA_RUN_ID, runId)
                    .putExtra(AlarmReceiver.EXTRA_GENERATION, running.timerGeneration),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val h = running.habit
            val dismiss = PendingIntent.getBroadcast(
                context, 2,
                Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_DISMISS_TIMER)
                    .setData(android.net.Uri.parse("dothabits://dismiss/$runId/${running.timerGeneration}"))
                    .putExtra(ActionReceiver.EXTRA_RUN_ID, runId)
                    .putExtra(AlarmReceiver.EXTRA_GENERATION, running.timerGeneration),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val duration = TimerMath.formatDuration(running.tileSessionProgress!!.totalSeconds)
            val text = if (h.sessions > 1) {
                val n = (TimerMath.sessionsDone(running.value, h.sessionSeconds, h.sessions) + 1).coerceAtMost(h.sessions)
                context.getString(R.string.timer_session, n, h.sessions, duration)
            } else context.getString(R.string.timer_single, duration)
            val n = NotificationCompat.Builder(context, CH_TIMER)
                .setSmallIcon(R.drawable.ic_stat_dot)
                .setContentTitle(h.name)
                .setContentText(text)
                .setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(TimerProgress.MAX)))
                        .setProgress(progress.elapsed),
                )
                .setRequestPromotedOngoing(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(endMs)
                .setTimeoutAfter((expiresAt - nowMs).coerceAtLeast(1L))
                .setShowWhen(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setContentIntent(openApp(context, running.habit.id))
                .setDeleteIntent(dismiss)
                .addAction(0, context.getString(R.string.pause), pause)
                .build()
            nm.notify(ID_TIMER, n)
        }

        // Remove a recovery notification left by an older app version.
        nm.cancel(ID_REVIEW)
    }

    /** A timer stopped itself at the end of a session. [done] is whole sessions finished today. */
    @Suppress("MissingPermission")
    fun sessionFinished(context: Context, habit: Habit, done: Int) {
        if (!canPost(context)) return
        val all = done >= habit.sessions
        NotificationManagerCompat.from(context).notify(
            ID_GOAL,
            NotificationCompat.Builder(context, CH_SESSION_DONE)
                .setSmallIcon(R.drawable.ic_stat_dot)
                .setContentTitle(
                    if (all) context.getString(R.string.goal_done_title, habit.name)
                    else if (done == 0) context.getString(R.string.session_finished_title, habit.name)
                    else context.getString(R.string.session_done_title, habit.name, done, habit.sessions),
                )
                .setContentText(
                    if (all) context.getString(R.string.goal_done_text, TimerMath.formatDuration(habit.dailyGoalUnits))
                    else context.getString(R.string.session_done_text),
                )
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(openApp(context, habit.id))
                .setAutoCancel(true)
                .build(),
        )
    }

    @Suppress("MissingPermission")
    fun reminder(context: Context, habit: Habit) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(
            REMINDER_BASE + habit.id.toInt(),
            NotificationCompat.Builder(context, CH_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_dot)
                .setContentTitle(habit.name)
                .setContentText(context.getString(R.string.reminder_text))
                .setContentIntent(openApp(context, habit.id))
                .setAutoCancel(true)
                .build(),
        )
    }
}
