package com.madebygps.dothabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate

class HabitRulesTest {
    // 2026-06-01 is a Monday.
    private val mon = LocalDate.of(2026, 6, 1)
    private fun d(offset: Int): LocalDate = mon.plusDays(offset.toLong())

    private fun habit(
        type: HabitType = HabitType.COUNT,
        target: Int = 1,
        schedule: Schedule = Schedule.Daily,
        negative: Boolean = false,
        created: LocalDate = mon,
    ) = Habit(id = 1, name = "h", icon = "dot", type = type, dailyTarget = target, schedule = schedule, isNegative = negative, createdOn = created)

    private fun values(vararg pairs: Pair<Int, Long>) = pairs.associate { d(it.first) to it.second }

    @Test fun dailyStreakCountsConsecutiveDays() {
        val s = HabitRules.streaks(habit(), d(4), MONDAY, values(0 to 1, 1 to 1, 2 to 1, 3 to 1, 4 to 1))
        assertEquals(5, s.current); assertEquals(5, s.best); assertEquals(StreakUnit.DAYS, s.unit)
    }

    @Test fun unmetTodayIsPendingNotBroken() {
        val s = HabitRules.streaks(habit(), d(3), MONDAY, values(0 to 1, 1 to 1, 2 to 1))
        assertEquals(3, s.current)
    }

    @Test fun missedDayResetsDailyStreakButKeepsBest() {
        val s = HabitRules.streaks(habit(), d(5), MONDAY, values(0 to 1, 1 to 1, 2 to 1, 4 to 1, 5 to 1))
        assertEquals(2, s.current); assertEquals(3, s.best)
    }

    @Test fun weekdayScheduleDoesNotPenaliseRestDays() {
        // Mon/Wed/Fri habit. Done every scheduled day for two weeks; nothing on rest days.
        val h = habit(schedule = Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val v = values(0 to 1, 2 to 1, 4 to 1, 7 to 1, 9 to 1, 11 to 1)
        val s = HabitRules.streaks(h, d(13), MONDAY, v) // Sunday, rest day
        assertEquals(6, s.current)
        assertEquals(DayStatus.REST, HabitRules.dayStatus(h, d(1), d(13), v))
        assertEquals(DayStatus.MISSED, HabitRules.dayStatus(h, d(14), d(15), v))
    }

    @Test fun multiCompletionDayNeedsAllCompletions() {
        val meds = habit(target = 2)
        val v = values(0 to 2, 1 to 1, 2 to 2)
        assertEquals(DayStatus.PARTIAL, HabitRules.dayStatus(meds, d(1), d(3), v))
        val s = HabitRules.streaks(meds, d(2), MONDAY, v)
        assertEquals(1, s.current); assertEquals(1, s.best)
    }

    @Test fun workoutNeedsFourDistinctDaysNotFourSameDayCompletions() {
        val workout = habit(schedule = Schedule.daysPerWeek(4))
        // Four completions crammed into Monday (e.g. via backfill) count as ONE day.
        val crammed = values(0 to 4)
        assertEquals(1 to 4, HabitRules.weekProgress(workout, d(0), d(6), MONDAY, crammed))
        val spread = values(0 to 1, 2 to 1, 4 to 1, 5 to 1)
        assertEquals(4 to 4, HabitRules.weekProgress(workout, d(0), d(6), MONDAY, spread))
    }

    @Test fun weeklyStreakCountsConsecutiveMetWeeks() {
        val workout = habit(schedule = Schedule.daysPerWeek(4))
        val v = HashMap<LocalDate, Long>()
        for (w in 0 until 3) for (day in listOf(0, 2, 4, 5)) v[d(w * 7 + day)] = 1
        val s = HabitRules.streaks(workout, d(3 * 7 + 1), MONDAY, v) // Tuesday of week 4, nothing yet
        assertEquals(3, s.current) // current week still open -> pending, not a reset
        assertEquals(StreakUnit.WEEKS, s.unit)
    }

    @Test fun weeklyStreakResetsOnlyWhenWeekClosesBelowGoal() {
        val workout = habit(schedule = Schedule.daysPerWeek(4))
        val v = HashMap<LocalDate, Long>()
        for (day in listOf(0, 2, 4, 5)) v[d(day)] = 1
        for (day in listOf(7, 9)) v[d(day)] = 1 // week 2 only 2 days
        // On Sunday of week 2 the week is still open.
        assertEquals(1, HabitRules.streaks(workout, d(13), MONDAY, v).current)
        // Monday of week 3: week 2 closed below goal.
        val after = HabitRules.streaks(workout, d(14), MONDAY, v)
        assertEquals(0, after.current); assertEquals(1, after.best)
    }

    @Test fun creationWeekIsGraceWeek() {
        // Created on a Friday: the partial first week can't reasonably reach 4 days.
        val workout = habit(schedule = Schedule.daysPerWeek(4), created = d(4))
        val v = HashMap<LocalDate, Long>()
        v[d(4)] = 1
        for (day in listOf(7, 8, 9, 10)) v[d(day)] = 1
        assertEquals(1, HabitRules.streaks(workout, d(14), MONDAY, v).current)
    }

    @Test fun weekStartIsConfigurable() {
        val h = habit(schedule = Schedule.timesPerWeek(3))
        val v = values(5 to 1, 6 to 1, 7 to 1) // Sat, Sun, Mon
        assertEquals(2 to 3, HabitRules.weekProgress(h, d(6), d(7), MONDAY, v))
        assertEquals(3 to 3, HabitRules.weekProgress(h, d(7), d(7), DayOfWeek.SATURDAY, v))
        assertEquals(d(6), HabitRules.weekStart(d(7), SUNDAY))
    }

    @Test fun timesPerWeekAllowsSameDayRepeats() {
        val h = habit(schedule = Schedule.timesPerWeek(3))
        assertEquals(3 to 3, HabitRules.weekProgress(h, d(0), d(0), MONDAY, values(0 to 3)))
    }

    @Test fun negativeHabitWithinAllowanceKeepsStreak() {
        val h = habit(negative = true, target = 0)
        val v = values(2 to 1)
        val s = HabitRules.streaks(h, d(5), MONDAY, v)
        assertEquals(2, s.current) // days 3, 4 (today pending)
        assertEquals(2, s.best)
        assertEquals(DayStatus.MISSED, HabitRules.dayStatus(h, d(2), d(5), v))
        assertEquals(DayStatus.MET, HabitRules.dayStatus(h, d(1), d(5), v))
    }

    @Test fun negativeHabitBreaksImmediatelyWhenTodayExceeded() {
        val h = habit(negative = true, target = 1)
        assertEquals(0, HabitRules.streaks(h, d(3), MONDAY, values(3 to 2)).current)
        assertEquals(3, HabitRules.streaks(h, d(3), MONDAY, values(3 to 1)).current)
    }

    @Test fun backfillAndUndoRecalculate() {
        val h = habit()
        val v = HashMap(values(0 to 1, 2 to 1, 3 to 1))
        assertEquals(2, HabitRules.streaks(h, d(3), MONDAY, v).current)
        v[d(1)] = 1 // backfill the gap
        assertEquals(4, HabitRules.streaks(h, d(3), MONDAY, v).current)
        v.remove(d(2)) // undo a historical entry
        val s = HabitRules.streaks(h, d(3), MONDAY, v)
        assertEquals(1, s.current); assertEquals(2, s.best)
    }

    @Test fun backfillBeforeCreationCountsButAbsenceDoesNotBreak() {
        val h = habit(created = d(7))
        val v = values(5 to 1, 6 to 1, 7 to 1)
        assertEquals(3, HabitRules.streaks(h, d(7), MONDAY, v).current)
        assertEquals(DayStatus.BEFORE_START, HabitRules.dayStatus(h, d(2), d(7), v))
    }

    @Test fun timedGoalUsesSeconds() {
        val read = habit(type = HabitType.TIMED, target = 60)
        assertFalse(HabitRules.isDayMet(read, 59 * 60))
        assertTrue(HabitRules.isDayMet(read, 3600))
    }

    @Test fun completionRateUsesClosedScheduledDays() {
        val h = habit(created = d(0))
        assertNull(HabitRules.completionRate(h, d(0), MONDAY, emptyMap()))
        assertEquals(0.5f, HabitRules.completionRate(h, d(4), MONDAY, values(0 to 1, 1 to 1))!!, 0.001f)
    }
}
