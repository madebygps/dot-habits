package com.madebygps.dothabits.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class StatsRange(val days: Int?, val label: String) {
    D30(30, "30D"), D60(60, "60D"), D90(90, "90D"), ALL(null, "ALL"),
}

/** successes / opportunities; rate is null when there was nothing to count. */
data class Tally(val met: Int = 0, val total: Int = 0) {
    val rate: Float? get() = if (total == 0) null else met.toFloat() / total
    operator fun plus(o: Tally) = Tally(met + o.met, total + o.total)
}

data class StatsBucket(val start: LocalDate, val tally: Tally)

data class HabitRangeStats(val habit: Habit, val tally: Tally, val streaks: StreakStats)

data class RangeStats(
    val from: LocalDate,
    val to: LocalDate,
    val overall: Tally,
    /** Same-length period right before [from]; null for ALL. */
    val previous: Tally?,
    val buckets: List<StatsBucket>,
    val monthlyBuckets: Boolean,
    /** Monday..Sunday, daily-scheduled habits only. */
    val weekdays: Map<DayOfWeek, Tally>,
    val habits: List<HabitRangeStats>,
)

/**
 * Range statistics for the Statistics screen. Only *closed* opportunities count, so today and the
 * current week never drag a rate down:
 * - daily and selected-weekday habits: each scheduled day up to yesterday;
 * - days/week and times/week habits: each finished week, attributed to its first day.
 * Unscheduled days are never counted. A habit counts from its creation or its earliest logged
 * day, whichever is earlier (so imported history is included).
 */
object Stats {

    fun compute(
        histories: List<HabitHistory>,
        today: LocalDate,
        firstDay: DayOfWeek,
        range: StatsRange,
    ): RangeStats {
        val to = today.minusDays(1)
        val earliest = histories.minOfOrNull { start(it) } ?: to
        val from = range.days?.let { today.minusDays(it.toLong()) } ?: minOf(earliest, to)
        val perHabit = histories.map { h -> h to opportunities(h, today, firstDay, from, to) }
        val overall = perHabit.fold(Tally()) { acc, (_, ops) -> acc + ops.tally() }

        val previous = range.days?.let { n ->
            val pTo = from.minusDays(1)
            val pFrom = from.minusDays(n.toLong())
            histories.fold(Tally()) { acc, h -> acc + opportunities(h, today, firstDay, pFrom, pTo).tally() }
        }

        val monthly = ChronoUnit.WEEKS.between(from, to) > 26
        val keyOf: (LocalDate) -> LocalDate =
            if (monthly) { d -> d.withDayOfMonth(1) } else { d -> HabitRules.weekStart(d, firstDay) }
        val all = perHabit.flatMap { it.second }
        val buckets = generateSequence(keyOf(from)) { if (monthly) it.plusMonths(1) else it.plusWeeks(1) }
            .takeWhile { !it.isAfter(to) }
            .map { key -> StatsBucket(key, all.filter { keyOf(it.date) == key }.tally()) }
            .toList()

        val weekdays = DayOfWeek.entries.associateWith { dow ->
            perHabit.filter { !it.first.habit.schedule.isWeekly }
                .flatMap { it.second }
                .filter { it.date.dayOfWeek == dow }
                .tally()
        }

        val habits = perHabit.map { (h, ops) ->
            HabitRangeStats(h.habit, ops.tally(), HabitRules.streaks(h.habit, today, firstDay, h.values))
        }
        return RangeStats(from, to, overall, previous, buckets, monthly, weekdays, habits)
    }

    private data class Op(val date: LocalDate, val met: Boolean)

    private fun List<Op>.tally() = Tally(count { it.met }, size)

    private fun start(h: HabitHistory): LocalDate {
        val logged = h.values.filterValues { it > 0 }.keys.minOrNull()
        return listOfNotNull(h.habit.createdOn, logged).min()
    }

    private fun opportunities(h: HabitHistory, today: LocalDate, firstDay: DayOfWeek, from: LocalDate, to: LocalDate): List<Op> {
        val habit = h.habit
        val begin = maxOf(from, start(h))
        if (begin.isAfter(to)) return emptyList()
        if (habit.schedule.isWeekly) {
            val currentWeek = HabitRules.weekStart(today, firstDay)
            return generateSequence(HabitRules.weekStart(begin, firstDay)) { it.plusWeeks(1) }
                .takeWhile { it.isBefore(currentWeek) && !it.isAfter(to) }
                // A week belongs to the range it starts in.
                .filter { !it.isBefore(from) }
                .map { w -> Op(w, HabitRules.weekProgress(habit, w, today, firstDay, h.values)!!.let { it.first >= it.second }) }
                .toList()
        }
        return generateSequence(begin) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { habit.schedule.isScheduledOn(it) }
            .map { Op(it, HabitRules.isDayMet(habit, h.values[it] ?: 0L)) }
            .toList()
    }
}
