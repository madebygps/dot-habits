package com.madebygps.dothabits.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class StatsRange(val days: Int?, val label: String) {
    D30(30, "30 days"), D60(60, "60 days"), D90(90, "90 days"), ALL(null, "All time"),
}

/** successes / opportunities; rate is null when there was nothing to count. */
data class Tally(val met: Int = 0, val total: Int = 0) {
    val rate: Float? get() = if (total == 0) null else met.toFloat() / total
    operator fun plus(other: Tally) = Tally(met + other.met, total + other.total)
}

data class StatsBucket(val start: LocalDate, val tally: Tally)

data class RangeStats(
    val from: LocalDate,
    val to: LocalDate,
    val overall: Tally,
    /** Same-length period right before [from]; null for ALL. */
    val previous: Tally?,
    val buckets: List<StatsBucket>,
    val monthlyBuckets: Boolean,
    val hasHabits: Boolean,
    val missingStepGoals: Int,
)

/**
 * Only closed opportunities count: scheduled past days or finished weeks, attributed to the
 * week's first day. Unknown step goals are excluded. Imported history before creation is included.
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
        val all = histories.flatMap { opportunities(it, today, firstDay, from, to) }
        val previous = range.days?.let { days ->
            val previousTo = from.minusDays(1)
            val previousFrom = from.minusDays(days.toLong())
            histories.flatMap { opportunities(it, today, firstDay, previousFrom, previousTo) }.tally()
        }
        val monthly = ChronoUnit.WEEKS.between(from, to) > 26
        val keyOf: (LocalDate) -> LocalDate =
            if (monthly) { date -> date.withDayOfMonth(1) }
            else { date -> HabitRules.weekStart(date, firstDay) }
        val grouped = all.groupBy { keyOf(it.date) }
        val buckets = generateSequence(keyOf(from)) { if (monthly) it.plusMonths(1) else it.plusWeeks(1) }
            .takeWhile { !it.isAfter(to) }
            .map { key -> StatsBucket(key, grouped[key].orEmpty().tally()) }
            .toList()
        return RangeStats(
            from, to, all.tally(), previous, buckets, monthly,
            histories.isNotEmpty(), all.count { it.met == null },
        )
    }

    private data class Op(val date: LocalDate, val met: Boolean?)

    private fun List<Op>.tally() = Tally(count { it.met == true }, count { it.met != null })

    private fun start(history: HabitHistory): LocalDate {
        val logged = history.values.filterValues { it > 0 }.keys.minOrNull()
        return listOfNotNull(history.habit.createdOn, logged).min()
    }

    private fun opportunities(
        history: HabitHistory, today: LocalDate, firstDay: DayOfWeek, from: LocalDate, to: LocalDate,
    ): List<Op> {
        val habit = history.habit
        val begin = maxOf(from, start(history))
        if (begin.isAfter(to)) return emptyList()
        if (habit.schedule.isWeekly) {
            val currentWeek = HabitRules.weekStart(today, firstDay)
            return generateSequence(HabitRules.weekStart(begin, firstDay)) { it.plusWeeks(1) }
                .takeWhile { it.isBefore(currentWeek) && !it.isAfter(to) }
                .filter { !it.isBefore(from) }
                .map { week ->
                    val met = HabitRules.weekProgress(habit, week, today, firstDay, history.values)!!
                        .let { it.first >= it.second }
                    val missing = habit.type == HabitType.STEPS &&
                        (0L..6L).any { !week.plusDays(it).isBefore(begin) && week.plusDays(it) !in history.values }
                    Op(week, if (!met && missing) null else met)
                }
                .toList()
        }
        return generateSequence(begin) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { habit.schedule.isScheduledOn(it) }
            .map { date ->
                Op(date, if (habit.type == HabitType.STEPS && date !in history.values) null
                    else HabitRules.isDayMet(habit, history.values[date] ?: 0L))
            }
            .toList()
    }
}
