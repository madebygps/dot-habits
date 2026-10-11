package com.madebygps.dothabits.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

enum class StatsRange(val days: Int, val label: String) {
    D7(7, "7 days"), D30(30, "30 days"), D60(60, "60 days"), D90(90, "90 days"),
}

/** successes / opportunities; rate is null when there was nothing to count. */
data class Tally(val met: Int = 0, val total: Int = 0) {
    val rate: Float? get() = if (total == 0) null else met.toFloat() / total
    val percentage: String get() = rate?.let { "${(it * 100).roundToInt()}%" } ?: "--"
    val missed: Int get() = total - met
    fun changePoints(previous: Tally?): Int? {
        val currentRate = rate ?: return null
        val previousRate = previous?.rate ?: return null
        return ((currentRate - previousRate) * 100).roundToInt()
    }
    operator fun plus(other: Tally) = Tally(met + other.met, total + other.total)
}

data class StatsBucket(val start: LocalDate, val tally: Tally)

data class HabitStats(val habit: Habit, val current: Tally, val previous: Tally?) {
    val changePoints: Int? get() = current.changePoints(previous)
}

data class WeekdayStats(val day: DayOfWeek, val tally: Tally)

data class RangeStats(
    val from: LocalDate,
    val to: LocalDate,
    val overall: Tally,
    /** Same-length period right before [from]. */
    val previous: Tally?,
    val buckets: List<StatsBucket>,
    val monthlyBuckets: Boolean,
    val hasHabits: Boolean,
    val missingStepGoals: Int,
    val habits: List<HabitStats> = emptyList(),
    val weekdays: List<WeekdayStats> = emptyList(),
) {
    val previousTo: LocalDate get() = from.minusDays(1)
    val previousFrom: LocalDate get() = from.minusDays(ChronoUnit.DAYS.between(from, to) + 1)
    val changePoints: Int? get() = overall.changePoints(previous)
    val improving: List<HabitStats> get() = habits.filter { (it.changePoints ?: 0) > 0 }
        .sortedWith(compareByDescending<HabitStats> { it.changePoints }.thenBy { it.habit.id }).take(3)
    val declining: List<HabitStats> get() = habits.filter { (it.changePoints ?: 0) < 0 }
        .sortedWith(compareBy<HabitStats> { it.changePoints }.thenBy { it.habit.id }).take(3)
    val missedGoals: List<HabitStats> get() = habits.filter { it.current.missed > 0 }
        .sortedWith(compareByDescending<HabitStats> { it.current.missed }.thenBy { it.habit.id })
    val trendTitle: String get() = if (monthlyBuckets) "MONTHLY COMPLETION" else "WEEKLY COMPLETION"
}

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
        val from = today.minusDays(range.days.toLong())
        val currentByHabit = histories.map { opportunities(it, today, firstDay, from, to) }
        val all = currentByHabit.flatten()
        val previousTo = from.minusDays(1)
        val previousFrom = from.minusDays(range.days.toLong())
        val previousByHabit = histories.map { opportunities(it, today, firstDay, previousFrom, previousTo) }
        val previous = previousByHabit.flatten().tally()
        val habits = histories.mapIndexed { index, history ->
            HabitStats(history.habit, currentByHabit[index].tally(), previousByHabit[index].tally())
        }
        val daily = histories.indices.filter { !histories[it].habit.schedule.isWeekly }
            .flatMap { currentByHabit[it] }.groupBy { it.date.dayOfWeek }
        val weekdays = (0L..6L).map { offset ->
            val day = firstDay.plus(offset)
            WeekdayStats(day, daily[day].orEmpty().tally())
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
            histories.isNotEmpty(), all.count { it.met == null }, habits, weekdays,
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
