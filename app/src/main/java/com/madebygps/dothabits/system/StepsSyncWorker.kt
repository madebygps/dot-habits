package com.madebygps.dothabits.system

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.madebygps.dothabits.domain.HabitType
import com.madebygps.dothabits.dotApp
import java.util.concurrent.TimeUnit

/**
 * Deferred, battery-friendly refresh (every 15 minutes, WorkManager's minimum; battery-not-low).
 * Pulls step totals from Health Connect when background reads are permitted, confirms running
 * timers are alive, and refreshes widgets. While the app is open, steps are also read every
 * minute (Health Connect writes phone steps at most about once a minute).
 */
class StepsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.dotApp
        app.repository.touchAlive()
        syncSteps(applicationContext, days = 2)
        Refresh.afterDataChange(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "steps-sync"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<StepsSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        /** Reads steps only if a STEPS habit exists and permission is granted. Never invents data. */
        suspend fun syncSteps(context: Context, days: Int) {
            val app = context.dotApp
            val hasStepsHabit = app.repository.currentSnapshot().habits.any { it.habit.type == HabitType.STEPS }
            if (!hasStepsHabit) return
            val status = app.steps.status()
            if (!status.readGranted) return
            app.repository.cacheSteps(app.steps.readDailySteps(days))
        }
    }
}
