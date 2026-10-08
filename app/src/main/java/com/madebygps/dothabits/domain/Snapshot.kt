package com.madebygps.dothabits.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

enum class TodayStatus {
    /** Goal for today (or this week) met. */
    DONE,
    IN_PROGRESS,
    NOT_STARTED,
    /** WEEKDAYS habit on an unscheduled day. */
    REST,
    /** Avoid habit within allowance so far today. */
    ON_TRACK,
    /** Avoid habit exceeded its allowance today. */
    SLIPPED,
}

data class HabitToday(
    val habit: Habit,
    /** Today's value in stored units (count / seconds / steps); weekly total for TIMES_PER_WEEK. */
    val value: Long,
    /** Ring fill, 0..1. */
    val fraction: Float,
    val segments: Int,
    val status: TodayStatus,
    val streak: StreakStats,
    val week: Pair<Int, Int>?,
    val timerRunning: Boolean,
    val needsReview: Boolean,
    /** For STEPS habits: false when no Health Connect reading exists for today. */
    val hasData: Boolean = true,
) {
    val countsTowardToday: Boolean get() = status != TodayStatus.REST
    val isComplete: Boolean get() = status == TodayStatus.DONE || status == TodayStatus.ON_TRACK
}

data class ActiveTimer(val habitId: Long, val habitName: String, val todaySeconds: Long, val goalSeconds: Long, val running: Boolean)

/**
 * Single source of truth consumed by the app UI, Glance widgets and the Glyph Toy.
 * All three surfaces render from the same [TodaySnapshot] so they cannot disagree.
 */
data class TodaySnapshot(val date: LocalDate, val habits: List<HabitToday>, val activeTimer: ActiveTimer?) {
    val dueCount: Int get() = habits.count { it.countsTowardToday }
    val doneCount: Int get() = habits.count { it.countsTowardToday && it.isComplete }
    val overallFraction: Float get() = if (dueCount == 0) 0f else doneCount.toFloat() / dueCount

    companion object {
        val Empty = TodaySnapshot(LocalDate.MIN, emptyList(), null)
    }
}

/** Raw history for one habit needed to build its snapshot. Values keyed by day in stored units. */
data class HabitHistory(
    val habit: Habit,
    val values: Map<LocalDate, Long>,
    val sessions: List<TimerSession> = emptyList(),
    val stepsAvailableToday: Boolean = true,
)

object SnapshotBuilder {

    fun habitToday(h: HabitHistory, today: LocalDate, firstDay: DayOfWeek, @Suppress("UNUSED_PARAMETER") now: Instant): HabitToday {
        val habit = h.habit
        val todayValue = h.values[today] ?: 0L
        val week = HabitRules.weekProgress(habit, today, today, firstDay, h.values)
        val streak = HabitRules.streaks(habit, today, firstDay, h.values)
        val running = h.sessions.any { it.state == SessionState.RUNNING }
        val review = h.sessions.any { it.state == SessionState.NEEDS_REVIEW }

        val (value, fraction, status) = when {
            habit.isNegative -> {
                val slipped = todayValue > habit.dailyTarget
                Triple(todayValue, if (slipped) 0f else 1f, if (slipped) TodayStatus.SLIPPED else TodayStatus.ON_TRACK)
            }
            habit.schedule.kind == ScheduleKind.TIMES_PER_WEEK -> {
                val (done, goal) = week!!
                val f = (done.toFloat() / goal).coerceIn(0f, 1f)
                Triple(done.toLong(), f, statusFor(f, done > 0))
            }
            !habit.schedule.isScheduledOn(today) && todayValue == 0L -> Triple(0L, 0f, TodayStatus.REST)
            else -> {
                val f = (todayValue.toFloat() / habit.dailyGoalUnits.coerceAtLeast(1)).coerceIn(0f, 1f)
                val weekDone = week != null && week.first >= week.second
                val status = if (weekDone && f < 1f) TodayStatus.DONE else statusFor(f, todayValue > 0)
                Triple(todayValue, f, status)
            }
        }
        return HabitToday(
            habit = habit,
            value = value,
            fraction = fraction,
            segments = habit.ringSegments,
            status = status,
            streak = streak,
            week = week,
            timerRunning = running,
            needsReview = review,
            hasData = habit.type != HabitType.STEPS || h.stepsAvailableToday,
        )
    }

    private fun statusFor(f: Float, started: Boolean) = when {
        f >= 1f -> TodayStatus.DONE
        started -> TodayStatus.IN_PROGRESS
        else -> TodayStatus.NOT_STARTED
    }

    fun build(histories: List<HabitHistory>, today: LocalDate, firstDay: DayOfWeek, now: Instant): TodaySnapshot {
        val habits = histories.sortedBy { it.habit.position }.map { habitToday(it, today, firstDay, now) }
        val timerHabit = habits.firstOrNull { it.timerRunning }
            ?: habits.firstOrNull { it.needsReview }
        val active = timerHabit?.let {
            ActiveTimer(it.habit.id, it.habit.name, it.value, it.habit.dailyGoalUnits, it.timerRunning)
        }
        return TodaySnapshot(today, habits, active)
    }
}
