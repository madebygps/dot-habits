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
    val timerPaused: Boolean = false,
    /** For STEPS habits: false when no Health Connect reading exists for today. */
    val hasData: Boolean = true,
    /** App/widget current-session fill, anchored to a stored run rather than rounded day totals. */
    val tileSessionProgress: TileSessionProgress? = null,
    /** Fixed deadline of the live run, independent of day rollover or edits to its habit. */
    val timerEndsAt: Instant? = null,
    val runningSecondsRemaining: Long? = null,
    val timerSessionId: Long? = null,
    val timerGeneration: Long? = null,
) {
    /** TIMED only: seconds left in the current session (a full session once the last one ended). */
    val sessionRemaining: Long get() = runningSecondsRemaining ?: habit.sessionSeconds

    val countsTowardToday: Boolean get() = status != TodayStatus.REST
    val isComplete: Boolean get() = status == TodayStatus.DONE || status == TodayStatus.ON_TRACK

    val canControlTimer: Boolean get() = habit.type == HabitType.TIMED && !CompletionPolicy.timerGoalMet(habit, value)
}

/**
 * The timer the Glyph Toy shows and its long press starts/pauses: the running timer, otherwise
 * the timed habit picked on the toy, otherwise the first timed habit due today that isn't done
 * yet (home-screen order).
 */
data class ActiveTimer(
    val habitId: Long,
    val habitName: String,
    val todaySeconds: Long,
    val sessionSeconds: Long,
    val sessions: Int,
    val running: Boolean,
    /** Seconds left in the current session. */
    val sessionRemaining: Long,
    val icon: String = "",
    /** Timed habits due today and not done, in home order; the toy's hold gesture cycles them. */
    val choices: List<Long> = listOf(habitId),
    val endsAt: Instant? = null,
    val completedSessions: Int = TimerMath.sessionsDone(todaySeconds, sessionSeconds, sessions),
) {
    /** The habit a hold should switch to, or null when there's nothing else to pick or one is running. */
    val next: Long? get() = if (running || choices.size < 2) null
        else choices[(choices.indexOf(habitId) + 1).mod(choices.size)]

    val sessionsDone: Int get() = completedSessions
    val sessionFraction: Float get() =
        ((sessionSeconds - sessionRemaining).toDouble() / sessionSeconds.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
}

/**
 * Single source of truth consumed by the app UI, Glance widgets and the Glyph Toy.
 * All three surfaces render from the same [TodaySnapshot] so they cannot disagree.
 */
data class TodaySnapshot(val date: LocalDate, val habits: List<HabitToday>, val activeTimer: ActiveTimer?) {
    /** Never substitute another habit when the user has not picked one or it was deleted. */
    fun glyphHabit(id: Long?): HabitToday? = habits.firstOrNull { it.habit.id == id }

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

    fun habitToday(h: HabitHistory, today: LocalDate, firstDay: DayOfWeek, now: Instant): HabitToday {
        val habit = h.habit
        val todayValue = h.values[today] ?: 0L
        val week = HabitRules.weekProgress(habit, today, today, firstDay, h.values)
        val streak = HabitRules.streaks(habit, today, firstDay, h.values)
        val session = h.sessions.firstOrNull {
            habit.type == HabitType.TIMED && it.state != SessionState.CLOSED && it.epochDay == today.toEpochDay() &&
                !CompletionPolicy.timerGoalMet(habit, todayValue)
        }
        val timing = session?.let { TimerMath.timing(it, now) }
        val run = session?.takeIf { it.state == SessionState.RUNNING }
        val tileSessionProgress = session?.let {
            TileSessionProgress(it.limitSeconds, timing!!.remainingSeconds)
        }

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
            timerRunning = run != null,
            timerPaused = session?.state == SessionState.PAUSED,
            hasData = habit.type != HabitType.STEPS || h.stepsAvailableToday,
            tileSessionProgress = tileSessionProgress,
            timerEndsAt = timing?.endsAt,
            runningSecondsRemaining = timing?.remainingSeconds,
            timerSessionId = session?.id,
            timerGeneration = session?.generation,
        )
    }

    private fun statusFor(f: Float, started: Boolean) = when {
        f >= 1f -> TodayStatus.DONE
        started -> TodayStatus.IN_PROGRESS
        else -> TodayStatus.NOT_STARTED
    }

    fun build(
        histories: List<HabitHistory>,
        today: LocalDate,
        firstDay: DayOfWeek,
        now: Instant,
        preferredTimer: Long? = null,
    ): TodaySnapshot {
        val habits = histories.sortedBy { it.habit.position }.map { habitToday(it, today, firstDay, now) }
        val open = habits.filter { it.canControlTimer && (it.timerPaused || it.timerRunning || (it.countsTowardToday && !it.isComplete)) }
        val timerHabit = habits.firstOrNull { it.timerRunning }
            ?: open.firstOrNull { it.habit.id == preferredTimer }
            ?: open.firstOrNull()
        val active = timerHabit?.let {
            val choices = open.map { o -> o.habit.id }.ifEmpty { listOf(it.habit.id) }
            ActiveTimer(
                it.habit.id, it.habit.name, it.value, it.tileSessionProgress?.totalSeconds ?: it.habit.sessionSeconds, it.habit.sessions,
                it.timerRunning, it.sessionRemaining, it.habit.icon,
                if (it.habit.id in choices) choices else listOf(it.habit.id) + choices,
                it.timerEndsAt,
                TimerMath.sessionsDone(it.value, it.habit.sessionSeconds, it.habit.sessions),
            )
        }
        return TodaySnapshot(today, habits, active)
    }
}
