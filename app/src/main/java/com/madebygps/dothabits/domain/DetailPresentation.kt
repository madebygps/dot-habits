package com.madebygps.dothabits.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.text.NumberFormat

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

data class HeroProgress(val primary: String, val label: String)

data class CalendarStreak(val periods: List<HistoryPoint>) {
    val latest: HistoryPoint? get() = periods.lastOrNull()
    fun contains(date: LocalDate): Boolean = periods.any { date in it.start..it.end }

    fun connects(from: LocalDate, to: LocalDate): Boolean {
        val first = periods.indexOfFirst { date -> from in date.start..date.end }
        val second = periods.indexOfFirst { date -> to in date.start..date.end }
        return first >= 0 && second == first + 1
    }

    fun dayLinks(date: LocalDate): Pair<Boolean, Boolean> {
        val previous = periods.any { it.start < date }
        val next = periods.any { it.start > date }
        val active = contains(date)
        val bridge = !active && previous && next
        return (bridge || (active && previous)) to (bridge || (active && next))
    }
}

/** Presentation decisions shared with the app, with no clock or persistence side effects. */
object DetailPresentation {
    fun calendarStreaks(h: HabitHistory, today: LocalDate, firstDay: DayOfWeek): List<CalendarStreak> {
        val earliest = minOf(h.habit.createdOn, h.values.filterValues { it > 0 }.keys.minOrNull() ?: h.habit.createdOn)
        val runs = mutableListOf<CalendarStreak>()
        val periods = mutableListOf<HistoryPoint>()
        val weekly = h.habit.schedule.isWeekly
        var date = if (weekly) HabitRules.weekStart(earliest, firstDay) else earliest
        fun closeRun() {
            if (periods.isNotEmpty()) runs += CalendarStreak(periods.toList())
            periods.clear()
        }
        while (date <= today) {
            val point = if (weekly) weekPoint(h, date, today, firstDay) else dayPoint(h, date, today)
            when {
                !weekly && !h.habit.schedule.isScheduledOn(date) -> Unit
                point.status == DayStatus.MET -> periods += point
                weekly && (point.current || date <= HabitRules.weekStart(h.habit.createdOn, firstDay)) -> Unit
                !weekly && date < h.habit.createdOn && point.status == DayStatus.BEFORE_START -> Unit
                !weekly && date == today && point.status != DayStatus.MISSED -> Unit
                else -> closeRun()
            }
            date = if (weekly) date.plusWeeks(1) else date.plusDays(1)
        }
        closeRun()
        return runs
    }

    fun heroProgress(t: HabitToday): HeroProgress {
        val h = t.habit
        if (h.type == HabitType.TIMED) {
            val done = TimerMath.sessionsDone(t.value.coerceAtLeast(0), h.sessionSeconds, h.sessions)
            return if (t.canControlTimer) {
                HeroProgress(
                    TimerMath.formatClock(t.tileSessionProgress?.remainingSeconds ?: t.sessionRemaining),
                    "${if (t.timerPaused) "PAUSED" else "LEFT"} · $done/${h.sessions}",
                )
            } else HeroProgress("$done/${h.sessions}", if (t.status == TodayStatus.REST) "REST DAY" else "SESSIONS")
        }
        if (h.type == HabitType.STEPS) {
            return if (t.hasData) HeroProgress(NumberFormat.getIntegerInstance().format(t.value), "STEPS")
                else HeroProgress("--", "NO STEP DATA")
        }
        if (h.isNegative) return HeroProgress("${t.value}/${h.dailyTarget}", "SLIPS")
        t.week?.let { return HeroProgress("${it.first}/${it.second}", "THIS WEEK") }
        return HeroProgress("${t.value}/${h.dailyTarget}", if (t.status == TodayStatus.REST) "REST DAY" else "TODAY")
    }

    fun calendarStreak(h: HabitHistory, today: LocalDate, firstDay: DayOfWeek): CalendarStreak {
        var remaining = HabitRules.streaks(h.habit, today, firstDay, h.values).current
        val periods = mutableListOf<HistoryPoint>()
        var date = today
        while (remaining > 0) {
            val point = if (h.habit.schedule.isWeekly) weekPoint(h, date, today, firstDay)
                else dayPoint(h, date, today)
            if (point.status == DayStatus.MET &&
                (h.habit.schedule.isWeekly || h.habit.schedule.isScheduledOn(date))) {
                periods += point
                remaining--
            }
            date = if (h.habit.schedule.isWeekly) date.minusWeeks(1) else date.minusDays(1)
        }
        return CalendarStreak(periods.reversed())
    }

    fun schedule(h: Habit): String {
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
        if (h.type == HabitType.TIMED) {
            val sessions = "${h.sessions} ${if (h.sessions == 1) "session" else "sessions"} of ${h.dailyTarget} min"
            return when (h.schedule.kind) {
                ScheduleKind.DAILY -> "$sessions every day"
                ScheduleKind.WEEKDAYS -> "$sessions on $schedule"
                ScheduleKind.DAYS_PER_WEEK -> "$sessions per day, ${h.schedule.perWeek} days a week"
                ScheduleKind.TIMES_PER_WEEK -> "$sessions · $schedule"
            }
        }
        return if (h.schedule.kind == ScheduleKind.TIMES_PER_WEEK) schedule else "$target · $schedule"
    }

    fun timerContext(t: HabitToday): TimerContext {
        val h = t.habit
        val remaining = t.tileSessionProgress?.remainingSeconds ?: t.sessionRemaining
        val primary = when {
            t.timerRunning -> "${TimerMath.formatClock(remaining)} left"
            t.timerPaused -> "${TimerMath.formatClock(remaining)} remaining · paused"
            t.value >= h.dailyGoalUnits -> "Today complete"
            t.status == TodayStatus.REST -> "Rest day"
            else -> "${TimerMath.formatClock(remaining)} left"
        }
        return TimerContext(
            primary,
            schedule(h),
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
