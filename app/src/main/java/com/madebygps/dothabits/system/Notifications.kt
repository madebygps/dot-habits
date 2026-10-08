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
     * Ongoing timer notification. The system Chronometer ticks on its own, so no service or
     * wake-ups are needed while a timer runs; state lives in Room as timestamps.
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
            val goal = running.habit.dailyGoalUnits
            val n = NotificationCompat.Builder(context, CH_TIMER)
                .setSmallIcon(R.drawable.ic_stat_dot)
                .setContentTitle(running.habit.name)
                .setContentText(context.getString(R.string.timer_goal, TimerMath.formatDuration(goal)))
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - running.value * 1000)
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

    @Suppress("MissingPermission")
    fun timerGoalReached(context: Context, habit: Habit) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(
            ID_GOAL,
            NotificationCompat.Builder(context, CH_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_dot)
                .setContentTitle(context.getString(R.string.goal_reached_title, habit.name))
                .setContentText(context.getString(R.string.goal_reached_text))
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
