package com.madebygps.dothabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate

class StatsTest {
    private val start = LocalDate.of(2026, 6, 1)
    private val today = LocalDate.of(2026, 8, 31)

    private fun habit(id: Long, schedule: Schedule = Schedule.Daily, created: LocalDate = start, negative: Boolean = false) =
        Habit(id = id, name = "h$id", icon = "dot", type = HabitType.COUNT, dailyTarget = if (negative) 0 else 1,
            schedule = schedule, isNegative = negative, createdOn = created)

    private fun everyDay(from: LocalDate, to: LocalDate, pred: (LocalDate) -> Boolean = { true }) =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.filter(pred).associateWith { 1L }

    private fun compute(vararg histories: HabitHistory, range: StatsRange = StatsRange.D30) =
        Stats.compute(histories.toList(), today, MONDAY, range)

    @Test fun todayIsNeverCounted() {
        val stats = compute(HabitHistory(habit(1), everyDay(start, today.minusDays(1))))
        assertEquals(1f, stats.overall.rate!!, 0f)
        assertEquals(30, stats.overall.total)
        assertEquals(today.minusDays(1), stats.to)
    }

    @Test fun unscheduledDaysAreNotCounted() {
        val h = habit(1, Schedule.weekdays(TUESDAY, THURSDAY))
        val values = everyDay(start, today) { it.dayOfWeek == TUESDAY || it.dayOfWeek == THURSDAY }
        val stats = compute(HabitHistory(h, values))
        assertEquals(1f, stats.overall.rate!!, 0f)
        assertTrue(stats.overall.total in 8..9)
    }

    @Test fun weeklyHabitsCountFinishedWeeks() {
        val h = habit(1, Schedule.daysPerWeek(4))
        val values = everyDay(start, today) {
            val week = java.time.temporal.ChronoUnit.WEEKS.between(start, it)
            week % 2 == 0L && it.dayOfWeek.value <= 4
        }
        val stats = compute(HabitHistory(h, values), range = StatsRange.D90)
        assertEquals(12, stats.overall.total)
        assertEquals(6, stats.overall.met)
    }

    @Test fun previousPeriodComparison() {
        val values = everyDay(today.minusDays(30), today.minusDays(1))
        val history = HabitHistory(habit(1), values)
        val stats = compute(history)
        assertEquals(1f, stats.overall.rate!!, 0f)
        assertEquals(0f, stats.previous!!.rate!!, 0f)
        assertEquals(100, stats.changePoints)
    }

    @Test fun percentageDistinguishesUnmeasuredFromZero() {
        assertEquals("--", Tally().percentage)
        assertEquals("0%", Tally(0, 10).percentage)
        assertEquals("67%", Tally(2, 3).percentage)
        assertEquals("100%", Tally(10, 10).percentage)
    }

    @Test fun comparisonUsesSignedRoundedPercentagePoints() {
        val stats = compute(HabitHistory(habit(1), emptyMap()))
        assertEquals(-11, stats.copy(overall = Tally(77, 100), previous = Tally(88, 100)).changePoints)
        assertEquals(11, stats.copy(overall = Tally(88, 100), previous = Tally(77, 100)).changePoints)
        assertEquals(0, stats.copy(overall = Tally(77, 100), previous = Tally(77, 100)).changePoints)
        assertNull(stats.copy(overall = Tally(), previous = Tally(1, 1)).changePoints)
        assertNull(stats.copy(overall = Tally(1, 1), previous = Tally()).changePoints)
    }

    @Test fun trendTitleMatchesBucketPeriods() {
        val stats = compute()
        assertEquals("WEEKLY COMPLETION", stats.copy(monthlyBuckets = false).trendTitle)
        assertEquals("MONTHLY COMPLETION", stats.copy(monthlyBuckets = true).trendTitle)
    }

    @Test fun habitCountsFromCreationOrEarliestLog() {
        val newer = habit(1, created = today.minusDays(10))
        assertEquals(10, compute(HabitHistory(newer, emptyMap()), range = StatsRange.D90).overall.total)
        val imported = everyDay(today.minusDays(40), today.minusDays(1))
        assertEquals(40, compute(HabitHistory(newer, imported), range = StatsRange.D90).overall.total)
    }

    @Test fun overallPoolsGoalsWithoutAddingRawUnits() {
        val a = habit(1).copy(dailyTarget = 2)
        val values = everyDay(start, today).mapValues { 200L }
        val stats = compute(HabitHistory(a, values), HabitHistory(habit(2), emptyMap()))
        assertEquals(Tally(30, 60), stats.overall)
        assertEquals(0.5f, stats.overall.rate!!, 0f)
    }

    @Test fun negativeHabitsMeetGoalWhenAvoided() {
        val slips = mapOf(today.minusDays(1) to 1L, today.minusDays(2) to 1L)
        assertEquals(28, compute(HabitHistory(habit(1, negative = true), slips)).overall.met)
    }

    @Test fun supportedRangesAreRecentAndBucketsAreWeekly() {
        assertEquals(listOf(7, 30, 60, 90), StatsRange.entries.map { it.days })
        val h = habit(1, created = LocalDate.of(2025, 1, 1))
        val short = compute(HabitHistory(h, emptyMap()), range = StatsRange.D90)
        assertFalse(short.monthlyBuckets)
        assertTrue(short.buckets.size in 13..14)
        assertTrue(short.buckets.all { it.start.dayOfWeek == MONDAY })
        assertEquals(short.overall.total, short.buckets.sumOf { it.tally.total })
    }

    @Test fun weekStartIsRespected() {
        val stats = Stats.compute(listOf(HabitHistory(habit(1), emptyMap())), today, DayOfWeek.SUNDAY, StatsRange.D30)
        assertTrue(stats.buckets.all { it.start.dayOfWeek == DayOfWeek.SUNDAY })
    }

    @Test fun measuredZeroStepsCountAsUnmetButAbsentStepsDoNot() {
        val steps = habit(1).copy(type = HabitType.STEPS, dailyTarget = 100)
        val stats = compute(HabitHistory(steps, mapOf(today.minusDays(1) to 0L, today.minusDays(2) to 100L)))
        assertEquals(Tally(1, 2), stats.overall)
        assertEquals(28, stats.missingStepGoals)
        assertEquals(stats.overall, stats.buckets.fold(Tally()) { tally, bucket -> tally + bucket.tally })
    }

    @Test fun weeklyStepsCanBeKnownMetDespiteMissingDaysButNotKnownMissed() {
        val steps = habit(1, Schedule.daysPerWeek(1)).copy(type = HabitType.STEPS, dailyTarget = 100)
        val stats = compute(HabitHistory(steps, mapOf(today.minusDays(7) to 100L)))
        assertEquals(Tally(1, 1), stats.overall)
        assertTrue(stats.missingStepGoals > 0)
    }

    @Test fun emptyHistoryDoesNotInventGoals() {
        val stats = compute()
        assertEquals(Tally(), stats.overall)
        assertFalse(stats.hasHabits)
        assertEquals(0, stats.missingStepGoals)
    }

    @Test fun everyRangeControlsOverallComparisonAndAllTrendBuckets() {
        val history = HabitHistory(habit(1, created = today.minusDays(200)), everyDay(today.minusDays(30), today))
        StatsRange.entries.forEach { range ->
            val stats = compute(history, range = range)
            val expectedDays = range.days
            assertEquals(today.minusDays(expectedDays.toLong()), stats.from)
            assertEquals(Tally(minOf(30, expectedDays), expectedDays), stats.overall)
            assertEquals(stats.overall, stats.buckets.fold(Tally()) { tally, bucket -> tally + bucket.tally })
            assertEquals(Tally(if (range == StatsRange.D7) 7 else 0, expectedDays), stats.previous)
            assertEquals(stats.from.minusDays(expectedDays.toLong()), stats.previousFrom)
            assertEquals(stats.from.minusDays(1), stats.previousTo)
        }
    }

    @Test fun perHabitTalliesPreserveOverallTotalsAndComparisons() {
        val histories = arrayOf(
            HabitHistory(habit(1), everyDay(today.minusDays(30), today.minusDays(1))),
            HabitHistory(habit(2), everyDay(today.minusDays(60), today.minusDays(31))),
            HabitHistory(habit(3, Schedule.daysPerWeek(3)), emptyMap()),
        )
        StatsRange.entries.forEach { range ->
            val stats = compute(*histories, range = range)
            assertEquals(stats.overall, stats.habits.fold(Tally()) { tally, habit -> tally + habit.current })
            assertEquals(stats.previous, stats.habits.fold(Tally()) { tally, habit -> tally + habit.previous!! })
            assertEquals(stats.overall.missed, stats.missedGoals.sumOf { it.current.missed })
        }
        val stats = compute(*histories)
        assertEquals(listOf(1L), stats.improving.map { it.habit.id })
        assertEquals(listOf(2L), stats.declining.map { it.habit.id })
    }

    @Test fun changesExcludeMissingPeriodsAndRankTopThreeInEachDirection() {
        val stats = compute().copy(habits = listOf(
            HabitStats(habit(1), Tally(9, 10), Tally(1, 10)),
            HabitStats(habit(2), Tally(7, 10), Tally(1, 10)),
            HabitStats(habit(3), Tally(5, 10), Tally(1, 10)),
            HabitStats(habit(4), Tally(3, 10), Tally(1, 10)),
            HabitStats(habit(5), Tally(1, 10), Tally(9, 10)),
            HabitStats(habit(6), Tally(1, 10), Tally(7, 10)),
            HabitStats(habit(7), Tally(1, 10), Tally(5, 10)),
            HabitStats(habit(8), Tally(1, 10), Tally(3, 10)),
            HabitStats(habit(9), Tally(10, 10), Tally()),
            HabitStats(habit(10), Tally(), Tally(10, 10)),
            HabitStats(habit(11), Tally(1, 10), Tally(1, 10)),
            HabitStats(habit(12), Tally(100, 100), Tally(100, 100)),
            HabitStats(habit(13), Tally(500, 1000), Tally(499, 1000)),
        ))
        assertEquals(listOf(1L, 2L, 3L), stats.improving.map { it.habit.id })
        assertEquals(listOf(5L, 6L, 7L), stats.declining.map { it.habit.id })
        assertTrue((stats.improving + stats.declining).none { it.habit.id in 9L..13L })
    }

    @Test fun weekdaysExcludeWeeklyGoalsRestDaysAndUnknownSteps() {
        val monday = habit(1, Schedule.weekdays(MONDAY))
        val steps = habit(2).copy(type = HabitType.STEPS, dailyTarget = 100)
        val weekly = habit(3, Schedule.daysPerWeek(1))
        val stats = Stats.compute(listOf(
            HabitHistory(monday, everyDay(start, today)),
            HabitHistory(steps, mapOf(today.minusDays(1) to 100L, today.minusDays(2) to 0L)),
            HabitHistory(weekly, everyDay(start, today)),
        ), today, DayOfWeek.SUNDAY, StatsRange.D30)
        assertEquals(DayOfWeek.SUNDAY, stats.weekdays.first().day)
        assertEquals(Tally(4, 4), stats.weekdays.single { it.day == MONDAY }.tally)
        assertEquals(Tally(1, 1), stats.weekdays.single { it.day == DayOfWeek.SUNDAY }.tally)
        assertEquals(Tally(0, 1), stats.weekdays.single { it.day == DayOfWeek.SATURDAY }.tally)
        assertEquals(Tally(), stats.weekdays.single { it.day == TUESDAY }.tally)
        assertEquals(Tally(5, 6), stats.weekdays.fold(Tally()) { tally, day -> tally + day.tally })
    }

    @Test fun missesExcludeUnknownStepGoalsAndIncludeAvoidAndWeeklyGoals() {
        val avoid = habit(1, negative = true)
        val steps = habit(2).copy(type = HabitType.STEPS, dailyTarget = 100)
        val weekly = habit(3, Schedule.daysPerWeek(1))
        val stats = compute(
            HabitHistory(avoid, mapOf(today.minusDays(1) to 1L)),
            HabitHistory(steps, emptyMap()),
            HabitHistory(weekly, emptyMap()),
        )
        assertEquals(30, stats.missingStepGoals)
        assertEquals(listOf(3L, 1L), stats.missedGoals.map { it.habit.id })
        assertEquals(1, stats.missedGoals.single { it.habit.id == 1L }.current.missed)
        assertEquals(stats.overall.missed, stats.missedGoals.sumOf { it.current.missed })
    }
}
