package com.madebygps.dothabits.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** How progress for a habit is measured. */
enum class HabitType {
    /** Manual press-and-hold completions. dailyTarget = completions per day (1 = simple check). */
    COUNT,

    /**
     * Timed sessions: dailyTarget = minutes per session, [Habit.sessions] = sessions per day
     * (Read 1 × 60 min, Deep Work 4 × 25 min). Each run stops itself at the end of its session.
     */
    TIMED,

    /** Health Connect step count; dailyTarget = steps. */
    STEPS,
}

enum class ScheduleKind {
    /** Every day. */
    DAILY,

    /** Only on the selected weekdays; other days are rest days and never break a streak. */
    WEEKDAYS,

    /** Daily target must be met on [Schedule.perWeek] distinct days of the week. */
    DAYS_PER_WEEK,

    /** [Schedule.perWeek] completions in total during the week, any days, any split. */
    TIMES_PER_WEEK,
}

data class Schedule(
    val kind: ScheduleKind,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val perWeek: Int = 0,
) {
    val isWeekly: Boolean get() = kind == ScheduleKind.DAYS_PER_WEEK || kind == ScheduleKind.TIMES_PER_WEEK

    fun isScheduledOn(date: LocalDate): Boolean = when (kind) {
        ScheduleKind.WEEKDAYS -> date.dayOfWeek in weekdays
        else -> true
    }

    companion object {
        val Daily = Schedule(ScheduleKind.DAILY)
        fun weekdays(vararg days: DayOfWeek) = Schedule(ScheduleKind.WEEKDAYS, weekdays = days.toSet())
        fun daysPerWeek(n: Int) = Schedule(ScheduleKind.DAYS_PER_WEEK, perWeek = n)
        fun timesPerWeek(n: Int) = Schedule(ScheduleKind.TIMES_PER_WEEK, perWeek = n)
    }
}

data class Habit(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val type: HabitType,
    /** Completions (COUNT), minutes per session (TIMED) or steps (STEPS) per day. For avoid habits: allowed slips per day. */
    val dailyTarget: Int,
    val schedule: Schedule = Schedule.Daily,
    /** Avoidance habit: a day succeeds when logged slips stay at or below [dailyTarget]. */
    val isNegative: Boolean = false,
    val reminders: List<LocalTime> = emptyList(),
    val position: Int = 0,
    val createdOn: LocalDate,
    /** TIMED only: sessions per day; the daily goal is sessions × dailyTarget minutes. */
    val sessions: Int = 1,
) {
    /** TIMED only: length of one session in seconds. */
    val sessionSeconds: Long get() = dailyTarget.toLong() * 60L

    /** Daily goal expressed in stored units (count, seconds, steps). */
    val dailyGoalUnits: Long
        get() = when (type) {
            HabitType.TIMED -> sessionSeconds * sessions.coerceAtLeast(1)
            else -> dailyTarget.toLong()
        }

    /** Ring segments for multi-completion habits (e.g. meds twice a day). 0 = continuous ring. */
    val ringSegments: Int
        get() = when {
            type == HabitType.TIMED -> sessions.takeIf { it in 2..12 } ?: 0
            type != HabitType.COUNT || isNegative -> 0
            schedule.kind == ScheduleKind.TIMES_PER_WEEK -> schedule.perWeek.takeIf { it in 2..14 } ?: 0
            else -> dailyTarget.takeIf { it in 2..12 } ?: 0
        }
}

enum class SessionState { RUNNING, CLOSED, NEEDS_REVIEW }

/**
 * One timer run for a TIMED habit. [limitSeconds] is how long this run may last (what was left
 * of the current session when it started); null for runs from before sessions existed. Time is stored as wall-clock instants so
 * nothing depends on the process staying alive. [lastAlive] is the latest moment the
 * app positively observed the session running; after a reboot the session is put into
 * NEEDS_REVIEW and only time up to [lastAlive] is counted until the user decides.
 */
data class TimerSession(
    val id: Long = 0,
    val habitId: Long,
    val start: Instant,
    val end: Instant?,
    val state: SessionState,
    val lastAlive: Instant,
    val bootCount: Int,
    val limitSeconds: Long? = null,
)

/** A manual log (completion, slip, or backfilled amount) on a given day. */
data class Entry(
    val id: Long = 0,
    val habitId: Long,
    val date: LocalDate,
    /** Count for COUNT habits; signed seconds adjustment to recorded credit for TIMED habits. */
    val amount: Long,
    val createdAt: Instant,
)
