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
        val stats = compute(HabitHistory(h, values), range = StatsRange.ALL)
        assertEquals(13, stats.overall.total)
        assertEquals(7, stats.overall.met)
    }

    @Test fun previousPeriodComparison() {
        val values = everyDay(today.minusDays(30), today.minusDays(1))
        val history = HabitHistory(habit(1), values)
        val stats = compute(history)
        assertEquals(1f, stats.overall.rate!!, 0f)
        assertEquals(0f, stats.previous!!.rate!!, 0f)
        assertNull(compute(history, range = StatsRange.ALL).previous)
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

    @Test fun bucketsAreWeeklyUpTo26WeeksThenMonthly() {
        val h = habit(1, created = LocalDate.of(2025, 1, 1))
        val short = compute(HabitHistory(h, emptyMap()), range = StatsRange.D90)
        assertFalse(short.monthlyBuckets)
        assertTrue(short.buckets.size in 13..14)
        assertTrue(short.buckets.all { it.start.dayOfWeek == MONDAY })
        val all = compute(HabitHistory(h, emptyMap()), range = StatsRange.ALL)
        assertTrue(all.monthlyBuckets)
        assertTrue(all.buckets.all { it.start.dayOfMonth == 1 })
        assertEquals(all.overall.total, all.buckets.sumOf { it.tally.total })
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
            val expectedDays = range.days ?: 200
            assertEquals(today.minusDays(expectedDays.toLong()), stats.from)
            assertEquals(Tally(30, expectedDays), stats.overall)
            assertEquals(stats.overall, stats.buckets.fold(Tally()) { tally, bucket -> tally + bucket.tally })
            if (range == StatsRange.ALL) {
                assertNull(stats.previous)
            } else {
                assertEquals(Tally(0, expectedDays), stats.previous)
            }
        }
    }
}
