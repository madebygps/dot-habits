package com.madebygps.dothabits.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class StreakUnit { DAYS, WEEKS }

data class StreakStats(val current: Int, val best: Int, val unit: StreakUnit)

/** Per-day status used by history calendars. */
enum class DayStatus { MET, PARTIAL, MISSED, REST, PENDING, FUTURE, BEFORE_START }

/**
 * Pure schedule/streak rules. Everything is recomputed from raw history on every call,
 * so editing, backfilling or undoing an entry automatically "recalculates" streaks.
 *
 * Rules (also in README):
 *  - DAILY / WEEKDAYS: streak counts consecutive *scheduled* days that met the goal.
 *    Unscheduled (rest) days are skipped and never break or extend a streak.
 *    Today only breaks the streak once it is over (a positive habit) — an unmet today is "pending".
 *  - DAYS_PER_WEEK / TIMES_PER_WEEK: streak counts consecutive successful weeks. The
 *    current week is pending until it closes, so it never resets a streak early.
 *    The week the habit was created in is a grace week: it counts if met but never breaks.
 *  - Avoid (negative) habits succeed on a day when slips <= allowance. Today counts only
 *    once it is over, but an exceeded allowance breaks the streak immediately.
 */
object HabitRules {

    fun weekStart(date: LocalDate, firstDay: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))

    fun isDayMet(habit: Habit, value: Long): Boolean =
        if (habit.isNegative) value <= habit.dailyTarget else value >= habit.dailyGoalUnits

    fun dayStatus(habit: Habit, date: LocalDate, today: LocalDate, values: Map<LocalDate, Long>): DayStatus {
        if (date.isBefore(habit.createdOn)) {
            // Backfilled history before creation still shows as met/partial for transparency.
            val v = values[date] ?: 0L
            return if (v > 0 && !habit.isNegative) {
                if (isDayMet(habit, v)) DayStatus.MET else DayStatus.PARTIAL
            } else DayStatus.BEFORE_START
        }
        if (date.isAfter(today)) return DayStatus.FUTURE
        val v = values[date] ?: 0L
        if (habit.schedule.kind == ScheduleKind.TIMES_PER_WEEK) {
            return if (v > 0) DayStatus.MET else if (date == today) DayStatus.PENDING else DayStatus.REST
        }
        if (!habit.schedule.isScheduledOn(date)) {
            return if (v > 0 && !habit.isNegative && isDayMet(habit, v)) DayStatus.MET else DayStatus.REST
        }
        if (habit.isNegative) {
            return when {
                v > habit.dailyTarget -> DayStatus.MISSED
                date == today -> DayStatus.PENDING
                else -> DayStatus.MET
            }
        }
        return when {
            isDayMet(habit, v) -> DayStatus.MET
            date == today -> if (v > 0) DayStatus.PARTIAL else DayStatus.PENDING
            habit.schedule.kind == ScheduleKind.DAYS_PER_WEEK -> if (v > 0) DayStatus.PARTIAL else DayStatus.REST
            v > 0 -> DayStatus.PARTIAL
            else -> DayStatus.MISSED
        }
    }

    /** Progress toward the weekly goal: (achieved, goal) or null for non-weekly schedules. */
    fun weekProgress(
        habit: Habit,
        anyDayInWeek: LocalDate,
        today: LocalDate,
        firstDay: DayOfWeek,
        values: Map<LocalDate, Long>,
    ): Pair<Int, Int>? {
        if (!habit.schedule.isWeekly) return null
        val start = weekStart(anyDayInWeek, firstDay)
        val days = (0L until 7L).map { start.plusDays(it) }.filter { !it.isAfter(today) }
        val achieved = when (habit.schedule.kind) {
            ScheduleKind.DAYS_PER_WEEK -> days.count { isDayMet(habit, values[it] ?: 0L) && (values[it] ?: 0L) > 0 }
            else -> days.sumOf { values[it] ?: 0L }.toInt()
        }
        return achieved to habit.schedule.perWeek
    }

    fun streaks(habit: Habit, today: LocalDate, firstDay: DayOfWeek, values: Map<LocalDate, Long>): StreakStats =
        if (habit.schedule.isWeekly) weeklyStreaks(habit, today, firstDay, values)
        else dailyStreaks(habit, today, values)

    /**
     * Success rate over a recent window: closed scheduled days (last [days]) for daily
     * schedules, closed weeks (last [days]/7) for weekly ones. Null when nothing has closed yet.
     */
    fun completionRate(habit: Habit, today: LocalDate, firstDay: DayOfWeek, values: Map<LocalDate, Long>, days: Int = 30): Float? {
        if (habit.schedule.isWeekly) {
            val current = weekStart(today, firstDay)
            val weeks = (1..(days / 7)).map { current.minusWeeks(it.toLong()) }
                .filter { !it.plusDays(6).isBefore(habit.createdOn) && it.isAfter(weekStart(habit.createdOn, firstDay)) }
            if (weeks.isEmpty()) return null
            val met = weeks.count { w -> weekProgress(habit, w, today, firstDay, values)!!.let { it.first >= it.second } }
            return met.toFloat() / weeks.size
        }
        val closed = (1..days).map { today.minusDays(it.toLong()) }
            .filter { !it.isBefore(habit.createdOn) && habit.schedule.isScheduledOn(it) }
        if (closed.isEmpty()) return null
        return closed.count { isDayMet(habit, values[it] ?: 0L) }.toFloat() / closed.size
    }

    /** Number of days the goal was met (all time). */
    fun daysMet(habit: Habit, today: LocalDate, values: Map<LocalDate, Long>): Int =
        if (habit.isNegative) {
            var d = habit.createdOn
            var n = 0
            while (d.isBefore(today)) {
                if (habit.schedule.isScheduledOn(d) && isDayMet(habit, values[d] ?: 0L)) n++
                d = d.plusDays(1)
            }
            n
        } else values.count { (d, v) -> !d.isAfter(today) && v > 0 && isDayMet(habit, v) }

    private fun dailyStreaks(habit: Habit, today: LocalDate, values: Map<LocalDate, Long>): StreakStats {
        val earliestLogged = values.filterValues { it > 0 }.keys.minOrNull()
        var day = listOfNotNull(habit.createdOn, earliestLogged).min()
        var run = 0
        var best = 0
        while (!day.isAfter(today)) {
            val beforeStart = day.isBefore(habit.createdOn)
            if (habit.schedule.isScheduledOn(day)) {
                val v = values[day] ?: 0L
                val met = isDayMet(habit, v) && !(habit.isNegative && day == today)
                when {
                    beforeStart && (habit.isNegative || v == 0L) -> Unit // nothing known before tracking began
                    met -> { run++; best = maxOf(best, run) }
                    day == today && !(habit.isNegative && v > habit.dailyTarget) -> Unit // pending
                    else -> run = 0
                }
            }
            day = day.plusDays(1)
        }
        return StreakStats(run, best, StreakUnit.DAYS)
    }

    private fun weeklyStreaks(
        habit: Habit,
        today: LocalDate,
        firstDay: DayOfWeek,
        values: Map<LocalDate, Long>,
    ): StreakStats {
        val earliestLogged = values.filterValues { it > 0 }.keys.minOrNull()
        val creationWeek = weekStart(habit.createdOn, firstDay)
        var week = weekStart(listOfNotNull(habit.createdOn, earliestLogged).min(), firstDay)
        val currentWeek = weekStart(today, firstDay)
        var run = 0
        var best = 0
        while (!week.isAfter(currentWeek)) {
            val (achieved, goal) = weekProgress(habit, week, today, firstDay, values)!!
            val met = achieved >= goal && goal > 0
            when {
                met -> { run++; best = maxOf(best, run) }
                week == currentWeek -> Unit // week still open
                !week.isAfter(creationWeek) -> Unit // grace: partial first week / pre-creation backfill
                else -> run = 0
            }
            week = week.plusWeeks(1)
        }
        return StreakStats(run, best, StreakUnit.WEEKS)
    }
}
