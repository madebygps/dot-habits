package com.madebygps.dothabits.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class HistoryPoint(
    val start: LocalDate,
    val end: LocalDate,
    val status: DayStatus,
    val current: Boolean,
    val grace: Boolean = false,
    val missingSteps: Boolean = false,
) {
    val stateLabel: String get() = when {
        missingSteps -> "No step data"
        grace && status == DayStatus.PARTIAL -> "Partly done, creation week grace"
        grace && status == DayStatus.REST -> "Creation week, grace"
        else -> when (status) {
            DayStatus.MET -> "Goal met"
            DayStatus.PARTIAL -> "Partly done"
            DayStatus.MISSED -> "Missed"
            DayStatus.REST -> "Rest"
            DayStatus.PENDING -> "Pending"
            DayStatus.FUTURE -> "Future"
            DayStatus.BEFORE_START -> "Before tracking started"
        }
    }
}

data class TimerContext(val primary: String, val secondary: String)

/** Presentation decisions shared with the app, with no clock or persistence side effects. */
object DetailPresentation {
    fun schedule(h: Habit): String {
        if (h.type == HabitType.TIMED) return ""
        val target = when {
            h.type == HabitType.STEPS -> "${h.dailyTarget} steps"
            h.isNegative -> "≤ ${h.dailyTarget} per day"
            else -> "${h.dailyTarget}× per day"
        }
        val schedule = when (h.schedule.kind) {
            ScheduleKind.DAILY -> "every day"
            ScheduleKind.WEEKDAYS -> h.schedule.weekdays.sorted().joinToString(" ") {
                it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            }
            ScheduleKind.DAYS_PER_WEEK -> "${h.schedule.perWeek} different days a week"
            ScheduleKind.TIMES_PER_WEEK -> "${h.schedule.perWeek} times a week"
        }
        return if (h.schedule.kind == ScheduleKind.TIMES_PER_WEEK) schedule else "$target · $schedule"
    }

    fun timerContext(t: HabitToday): TimerContext {
        val h = t.habit
        val remaining = t.tileSessionProgress?.remainingSeconds ?: t.sessionRemaining
        val done = TimerMath.sessionsDone(t.value.coerceAtLeast(0), h.sessionSeconds, h.sessions)
        val primary = when {
            t.timerRunning -> "${TimerMath.formatClock(remaining)} left"
            t.timerPaused -> "${TimerMath.formatClock(remaining)} remaining · paused"
            t.value >= h.dailyGoalUnits -> "Today complete"
            t.status == TodayStatus.REST -> "Rest day"
            else -> "${TimerMath.formatClock(remaining)} left"
        }
        return TimerContext(
            primary,
            if (h.sessions == 1) "1 session · ${(t.tileSessionProgress?.totalSeconds ?: h.sessionSeconds) / 60} min"
            else "Session ${(done + 1).coerceAtMost(h.sessions.coerceAtLeast(1))} of ${h.sessions} · ${(t.tileSessionProgress?.totalSeconds ?: h.sessionSeconds) / 60} min each",
        )
    }

    fun dayPoint(h: HabitHistory, date: LocalDate, today: LocalDate): HistoryPoint = HistoryPoint(
        date, date, HabitRules.dayStatus(h.habit, date, today, h.values), date == today,
        missingSteps = h.habit.type == HabitType.STEPS && date >= h.habit.createdOn &&
            date <= today && date !in h.values,
    )

    fun dayProgress(h: HabitHistory, date: LocalDate, today: LocalDate): String {
        val point = dayPoint(h, date, today)
        if (point.missingSteps) return "NO STEP DATA"
        if (point.status == DayStatus.BEFORE_START) return "No history before tracking started"
        val habit = h.habit
        val value = h.values[date] ?: 0
        return when {
            habit.type == HabitType.TIMED ->
                "${value.coerceAtLeast(0) / habit.sessionSeconds.coerceAtLeast(1)} of ${habit.sessions} sessions complete"
            habit.type == HabitType.STEPS -> "$value / ${habit.dailyTarget} steps"
            habit.isNegative -> "$value / ${habit.dailyTarget} slips allowed"
            habit.schedule.kind == ScheduleKind.TIMES_PER_WEEK -> "$value completions"
            else -> "$value / ${habit.dailyTarget} completions"
        }
    }

    fun weekPoint(h: HabitHistory, date: LocalDate, today: LocalDate, firstDay: DayOfWeek): HistoryPoint {
        val start = HabitRules.weekStart(date, firstDay)
        val end = start.plusDays(6)
        val current = start == HabitRules.weekStart(today, firstDay)
        val grace = start == HabitRules.weekStart(h.habit.createdOn, firstDay)
        val (achieved, goal) = HabitRules.weekProgress(h.habit, start, today, firstDay, h.values)!!
        val status = when {
            start > today -> DayStatus.FUTURE
            end < h.habit.createdOn && achieved == 0 -> DayStatus.BEFORE_START
            achieved >= goal -> DayStatus.MET
            achieved > 0 -> DayStatus.PARTIAL
            current -> DayStatus.PENDING
            grace || end < h.habit.createdOn -> DayStatus.REST
            else -> DayStatus.MISSED
        }
        val knownDays = (0L..6L).map { start.plusDays(it) }.filter { it >= h.habit.createdOn && it <= today }
        val missingSteps = h.habit.type == HabitType.STEPS && knownDays.isNotEmpty() && knownDays.none { it in h.values }
        return HistoryPoint(start, end, status, current, grace, missingSteps)
    }

    fun recentChain(h: HabitHistory, today: LocalDate, firstDay: DayOfWeek): List<HistoryPoint> =
        (6 downTo 0).map { offset ->
            if (h.habit.schedule.isWeekly) weekPoint(h, today.minusWeeks(offset.toLong()), today, firstDay)
            else dayPoint(h, today.minusDays(offset.toLong()), today)
        }

    fun closedDaysMet(h: HabitHistory, today: LocalDate): Int =
        if (h.habit.isNegative) HabitRules.daysMet(h.habit, today, h.values)
        else h.values.count { (date, value) ->
            date < today && h.habit.schedule.isScheduledOn(date) && value > 0 && HabitRules.isDayMet(h.habit, value)
        }
}
