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
                NotificationChannel(CH_REVIEW, context.getString(R.string.channel_review), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_SESSION_DONE, context.getString(R.string.channel_session_done), NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 150, 250, 150, 400)
                },
            ),
        )
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

    fun dismissTimer(context: Context, runId: Long) {
        context.getSharedPreferences(TIMER_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(DISMISSED_RUN, runId).apply()
    }

    /** The system owns the countdown; progress refreshes opportunistically without waking the phone. */
    @Suppress("MissingPermission")
    fun updateTimer(
        context: Context,
        snapshot: TodaySnapshot,
        runId: Long?,
        sessionEndMs: Long?,
        runLimitSeconds: Long?,
    ) {
        val nm = NotificationManagerCompat.from(context)
        val running = snapshot.habits.firstOrNull { it.timerRunning }
        val dismissed = runId != null && context.getSharedPreferences(TIMER_PREFS, Context.MODE_PRIVATE)
            .getLong(DISMISSED_RUN, -1L) == runId
        if (running == null || runId == null || dismissed || !canPost(context)) {
            nm.cancel(ID_TIMER)
        } else {
            val nowMs = System.currentTimeMillis()
            val endMs = sessionEndMs ?: (nowMs + running.sessionRemaining * 1_000L)
            val progress = requireNotNull(
                TimerProgress.from(
                    running,
                    runLimitSeconds ?: running.habit.sessionSeconds,
                    ((endMs - nowMs).coerceAtLeast(0) + 999L) / 1_000L,
                ),
            )
            val pause = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_PAUSE_TIMER)
                    .putExtra(ActionReceiver.EXTRA_HABIT_ID, running.habit.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val h = running.habit
            val dismiss = PendingIntent.getBroadcast(
                context, runId.toInt(),
                Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_DISMISS_TIMER)
                    .putExtra(ActionReceiver.EXTRA_RUN_ID, runId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val goal = TimerMath.formatDuration(h.dailyGoalUnits)
            val text = if (h.sessions > 1) {
                val n = (TimerMath.sessionsDone(running.value, h.sessionSeconds, h.sessions) + 1).coerceAtMost(h.sessions)
                context.getString(R.string.timer_session, n, h.sessions, goal)
            } else context.getString(R.string.timer_single, goal)
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
                .setTimeoutAfter((endMs - System.currentTimeMillis()).coerceAtLeast(1L))
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

        val review = snapshot.habits.filter { it.needsReview }
        if (review.isEmpty() || !canPost(context)) nm.cancel(ID_REVIEW)
        else nm.notify(
            ID_REVIEW,
            NotificationCompat.Builder(context, CH_REVIEW)
                .setSmallIcon(R.drawable.ic_stat_dot)
                .setContentTitle(context.getString(R.string.review_title))
                .setContentText(context.getString(R.string.review_text, review.joinToString { it.habit.name }))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.review_text, review.joinToString { it.habit.name })))
                .setContentIntent(openApp(context, review.first().habit.id))
                .setAutoCancel(true)
                .build(),
        )
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
