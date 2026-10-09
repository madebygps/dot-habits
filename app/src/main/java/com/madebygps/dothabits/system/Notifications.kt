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

    /**
     * Ongoing timer notification counting down the current session. The system Chronometer ticks
     * on its own, so no service or wake-ups are needed while a timer runs; state lives in Room.
     */
    @Suppress("MissingPermission")
    fun updateTimer(context: Context, snapshot: TodaySnapshot) {
        val nm = NotificationManagerCompat.from(context)
        val running = snapshot.habits.firstOrNull { it.timerRunning }
        if (running == null || !canPost(context)) {
            nm.cancel(ID_TIMER)
        } else {
            val pause = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_PAUSE_TIMER)
                    .putExtra(ActionReceiver.EXTRA_HABIT_ID, running.habit.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val h = running.habit
            val goal = TimerMath.formatDuration(h.dailyGoalUnits)
            val text = if (h.sessions > 1) {
                val n = (TimerMath.sessionsDone(running.value, h.sessionSeconds, h.sessions) + 1).coerceAtMost(h.sessions)
                context.getString(R.string.timer_session, n, h.sessions, goal)
            } else context.getString(R.string.timer_single, goal)
            val n = NotificationCompat.Builder(context, CH_TIMER)
                .setSmallIcon(R.drawable.ic_stat_dot)
                .setContentTitle(h.name)
                .setContentText(text)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(running.timerEndsAt?.toEpochMilli() ?: (System.currentTimeMillis() + running.sessionRemaining * 1000))
                .setShowWhen(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setContentIntent(openApp(context, running.habit.id))
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
