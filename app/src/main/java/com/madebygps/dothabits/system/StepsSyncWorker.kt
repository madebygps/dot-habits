package com.madebygps.dothabits.system

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.madebygps.dothabits.domain.HabitType
import com.madebygps.dothabits.dotApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

enum class StepsSyncOutcome {
    UPDATED,
    UNCHANGED,
    NO_STEPS_HABIT,
    READ_NOT_GRANTED,
    BACKGROUND_READ_NOT_GRANTED,
    READ_FAILED,
}

data class StepsSyncResult(
    val outcome: StepsSyncOutcome,
    val cacheChanged: Boolean = false,
    val hasRunningTimer: Boolean = false,
    val daysRead: Int = 0,
)

/**
 * Deferred, battery-friendly refresh (every 15 minutes, WorkManager's minimum; battery-not-low).
 * Pulls step totals from Health Connect when background reads are permitted, settles expired
 * timers through their lifecycle owner, and refreshes widgets. Steps are also read every
 * minute (Health Connect writes phone steps at most about once a minute).
 */
class StepsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val startedAtMs = System.currentTimeMillis()
        val started = SystemClock.elapsedRealtime()
        var timerLifecycleMs = 0L
        var stepSyncMs = 0L
        var refreshRequestMs = 0L
        var syncResult = StepsSyncResult(StepsSyncOutcome.READ_FAILED)
        var completion = "success"
        val app = applicationContext.dotApp
        try {
            val timerStarted = SystemClock.elapsedRealtime()
            TimerLifecycle.settle(app)
            timerLifecycleMs = SystemClock.elapsedRealtime() - timerStarted

            val syncStarted = SystemClock.elapsedRealtime()
            syncResult = syncSteps(applicationContext, days = 2, requireBackgroundRead = true)
            stepSyncMs = SystemClock.elapsedRealtime() - syncStarted

            if (!syncResult.cacheChanged && syncResult.hasRunningTimer) {
                val refreshStarted = SystemClock.elapsedRealtime()
                Refresh.afterDataChange(applicationContext, reason = "running-timer")
                refreshRequestMs = SystemClock.elapsedRealtime() - refreshStarted
            }
            return Result.success()
        } catch (e: CancellationException) {
            completion = "cancelled"
            throw e
        } catch (e: Throwable) {
            completion = "failure:${e.javaClass.simpleName}"
            Log.e(TAG, "background sync failed", e)
            throw e
        } finally {
            val diagnostic = BackgroundSyncDiagnostic(
                startedAtMs = startedAtMs,
                workId = id.toString(),
                runAttempt = runAttemptCount,
                completion = completion,
                outcome = syncResult.outcome,
                daysRead = syncResult.daysRead,
                cacheChanged = syncResult.cacheChanged,
                hadRunningTimer = syncResult.hasRunningTimer,
                timerLifecycleMs = timerLifecycleMs,
                stepSyncMs = stepSyncMs,
                refreshRequestMs = refreshRequestMs,
                totalMs = SystemClock.elapsedRealtime() - started,
            )
            withContext(NonCancellable) {
                BackgroundDiagnostics.record(applicationContext, diagnostic)
            }
        }
    }

    companion object {
        private const val TAG = "DotHabitsWorker"
        private const val NAME = "steps-sync"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<StepsSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** Reads steps only if a STEPS habit exists and permission is granted. Never invents data. */
        suspend fun syncSteps(
            context: Context,
            days: Int,
            requireBackgroundRead: Boolean = false,
        ): StepsSyncResult {
            val app = context.dotApp
            val snapshot = app.repository.currentSnapshot()
            val hasRunningTimer = snapshot.habits.any { it.timerRunning }
            val hasStepsHabit = snapshot.habits.any { it.habit.type == HabitType.STEPS }
            if (!hasStepsHabit) return StepsSyncResult(StepsSyncOutcome.NO_STEPS_HABIT, hasRunningTimer = hasRunningTimer)
            val status = app.steps.status()
            if (!status.readGranted) {
                return StepsSyncResult(StepsSyncOutcome.READ_NOT_GRANTED, hasRunningTimer = hasRunningTimer)
            }
            if (requireBackgroundRead && !status.backgroundGranted) {
                return StepsSyncResult(StepsSyncOutcome.BACKGROUND_READ_NOT_GRANTED, hasRunningTimer = hasRunningTimer)
            }
            val read = app.steps.readDailySteps(days)
                ?: return StepsSyncResult(StepsSyncOutcome.READ_FAILED, hasRunningTimer = hasRunningTimer)
            val changed = app.repository.cacheSteps(java.time.LocalDate.now().minusDays((days - 1).toLong()), read)
            return StepsSyncResult(
                outcome = if (changed) StepsSyncOutcome.UPDATED else StepsSyncOutcome.UNCHANGED,
                cacheChanged = changed,
                hasRunningTimer = hasRunningTimer,
                daysRead = read.size,
            )
        }
    }
}
