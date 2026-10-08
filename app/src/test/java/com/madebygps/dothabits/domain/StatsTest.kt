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
    // 2026-06-01 is a Monday; "today" is Monday 2026-08-31 (13 full weeks later).
    private val start = LocalDate.of(2026, 6, 1)
    private val today = LocalDate.of(2026, 8, 31)

    private fun habit(id: Long, schedule: Schedule = Schedule.Daily, created: LocalDate = start, negative: Boolean = false) =
        Habit(id = id, name = "h$id", icon = "dot", type = HabitType.COUNT, dailyTarget = if (negative) 0 else 1, schedule = schedule, isNegative = negative, createdOn = created)

    private fun everyDay(from: LocalDate, to: LocalDate, pred: (LocalDate) -> Boolean = { true }) =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.filter(pred).associateWith { 1L }

    private fun compute(vararg h: HabitHistory, range: StatsRange = StatsRange.D30) =
        Stats.compute(h.toList(), today, MONDAY, range)

    @Test fun todayIsNeverCounted() {
        val h = habit(1)
        val s = compute(HabitHistory(h, everyDay(start, today.minusDays(1))))
        assertEquals(1f, s.overall.rate!!, 0f)
        assertEquals(30, s.overall.total)
        assertEquals(today.minusDays(1), s.to)
    }

    @Test fun unscheduledDaysAreNotCounted() {
        val h = habit(1, Schedule.weekdays(TUESDAY, THURSDAY))
        // Done on every scheduled day, nothing else.
        val v = everyDay(start, today) { it.dayOfWeek == TUESDAY || it.dayOfWeek == THURSDAY }
        val s = compute(HabitHistory(h, v))
        assertEquals(1f, s.overall.rate!!, 0f)
        assertTrue(s.overall.total in 8..9)
        assertNull(s.weekdays[MONDAY]!!.rate)
        assertEquals(1f, s.weekdays[TUESDAY]!!.rate!!, 0f)
    }

    @Test fun weeklyHabitsCountFinishedWeeks() {
        // 4 distinct days/week; met in alternating weeks.
        val h = habit(1, Schedule.daysPerWeek(4))
        val v = everyDay(start, today) { d ->
            val week = java.time.temporal.ChronoUnit.WEEKS.between(start, d)
            week % 2 == 0L && d.dayOfWeek.value <= 4
        }
        val s = compute(HabitHistory(h, v), range = StatsRange.ALL)
        assertEquals(13, s.overall.total)
        assertEquals(7, s.overall.met)
        // Weekly habits stay out of the weekday chart.
        assertTrue(s.weekdays.values.all { it.total == 0 })
    }

    @Test fun previousPeriodComparison() {
        val h = habit(1)
        // Previous 30 days: all missed; last 30: all done.
        val v = everyDay(today.minusDays(30), today.minusDays(1))
        val s = compute(HabitHistory(h, v))
        assertEquals(1f, s.overall.rate!!, 0f)
        assertEquals(0f, s.previous!!.rate!!, 0f)
        assertNull(compute(HabitHistory(h, v), range = StatsRange.ALL).previous)
    }

    @Test fun habitCountsFromCreationOrEarliestLog() {
        val newer = habit(1, created = today.minusDays(10))
        val s = compute(HabitHistory(newer, emptyMap()), range = StatsRange.D90)
        assertEquals(10, s.overall.total)
        // Imported history before creation is included.
        val imported = everyDay(today.minusDays(40), today.minusDays(1))
        assertEquals(40, compute(HabitHistory(newer, imported), range = StatsRange.D90).overall.total)
    }

    @Test fun pooledOverallAndPerHabitRows() {
        val a = habit(1)
        val b = habit(2)
        val s = compute(HabitHistory(a, everyDay(start, today)), HabitHistory(b, emptyMap()))
        assertEquals(0.5f, s.overall.rate!!, 0f)
        assertEquals(listOf(1f, 0f), s.habits.map { it.tally.rate })
    }

    @Test fun negativeHabitsMeetGoalWhenAvoided() {
        val h = habit(1, negative = true)
        val slips = mapOf(today.minusDays(1) to 1L, today.minusDays(2) to 1L)
        val s = compute(HabitHistory(h, slips))
        assertEquals(28, s.overall.met)
    }

    @Test fun bucketsAreWeeklyUpTo26WeeksThenMonthly() {
        val h = habit(1, created = LocalDate.of(2025, 1, 1))
        val s90 = compute(HabitHistory(h, emptyMap()), range = StatsRange.D90)
        assertFalse(s90.monthlyBuckets)
        assertTrue(s90.buckets.size in 13..14)
        assertTrue(s90.buckets.all { it.start.dayOfWeek == MONDAY })
        val all = compute(HabitHistory(h, emptyMap()), range = StatsRange.ALL)
        assertTrue(all.monthlyBuckets)
        assertTrue(all.buckets.all { it.start.dayOfMonth == 1 })
        assertEquals(all.overall.total, all.buckets.sumOf { it.tally.total })
    }

    @Test fun weekStartIsRespected() {
        val h = habit(1)
        val s = Stats.compute(listOf(HabitHistory(h, emptyMap())), today, DayOfWeek.SUNDAY, StatsRange.D30)
        assertTrue(s.buckets.all { it.start.dayOfWeek == DayOfWeek.SUNDAY })
    }
}
