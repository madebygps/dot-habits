package com.madebygps.dothabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class DetailPresentationTest {
    private val today = LocalDate.of(2026, 10, 10)
    private val zone = ZoneId.of("UTC")
    private val now = today.atTime(12, 0).atZone(zone).toInstant()
    private val count = Habit(1, "Water", "dot", HabitType.COUNT, 2, createdOn = today.minusDays(20))
    private val timed = count.copy(type = HabitType.TIMED, dailyTarget = 25, sessions = 2)

    private fun snapshot(h: Habit = timed, value: Long = 0, runs: List<TimerSession> = emptyList()): HabitToday =
        SnapshotBuilder.habitToday(HabitHistory(h, mapOf(today to value), runs), today, DayOfWeek.MONDAY, now)

    @Test fun dailyChainIsSevenCalendarDaysNotSevenScheduledDays() {
        val h = count.copy(schedule = Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val chain = DetailPresentation.recentChain(HabitHistory(h, emptyMap()), today, DayOfWeek.MONDAY)
        assertEquals((6 downTo 0).map { today.minusDays(it.toLong()) }, chain.map { it.start })
        assertEquals(DayStatus.REST, chain.last().status)
        assertTrue(chain.last().current)
        assertEquals(1, chain.count { it.current })
        assertEquals(DayStatus.MISSED, chain.first { it.start.dayOfWeek == DayOfWeek.MONDAY }.status)
    }

    @Test fun chainPreservesPartialPendingBeforeStartAndBackfill() {
        val h = count.copy(createdOn = today.minusDays(2))
        val history = HabitHistory(h, mapOf(today.minusDays(1) to 1, today.minusDays(4) to 2))
        val chain = DetailPresentation.recentChain(history, today, DayOfWeek.MONDAY)
        assertEquals(DayStatus.BEFORE_START, chain.first().status)
        assertEquals(DayStatus.MET, chain[2].status)
        assertEquals(DayStatus.PARTIAL, chain[5].status)
        assertEquals(DayStatus.PENDING, chain.last().status)
        assertEquals(DayStatus.FUTURE, DetailPresentation.dayPoint(history, today.plusDays(1), today).status)
    }

    @Test fun avoidTodayIsPendingUntilSlipsExceedAllowance() {
        val avoid = count.copy(isNegative = true, dailyTarget = 1)
        assertEquals(DayStatus.PENDING, DetailPresentation.dayPoint(HabitHistory(avoid, emptyMap()), today, today).status)
        assertEquals(DayStatus.MISSED, DetailPresentation.dayPoint(HabitHistory(avoid, mapOf(today to 2)), today, today).status)
        assertEquals(DayStatus.MET, DetailPresentation.dayPoint(HabitHistory(avoid, emptyMap()), today.minusDays(1), today).status)
    }

    @Test fun missingStepReadingsAreNotInventedFailuresOrZeroTotals() {
        val h = count.copy(type = HabitType.STEPS)
        val missing = DetailPresentation.recentChain(HabitHistory(h, emptyMap()), today, DayOfWeek.MONDAY)
        assertTrue(missing.all { it.missingSteps })
        assertEquals("No step data", missing.last().stateLabel)
        val zero = DetailPresentation.dayPoint(HabitHistory(h, mapOf(today to 0)), today, today)
        assertFalse(zero.missingSteps)
        assertEquals(DayStatus.PENDING, zero.status)
    }

    @Test fun weeklyChainUsesConfiguredWeekStartAndDistinctDays() {
        val h = count.copy(schedule = Schedule.daysPerWeek(2), createdOn = today.minusWeeks(8))
        val currentStart = HabitRules.weekStart(today, DayOfWeek.SUNDAY)
        val values = mapOf(currentStart to 8L, currentStart.minusWeeks(1) to 2L, currentStart.minusWeeks(1).plusDays(1) to 2L)
        val chain = DetailPresentation.recentChain(HabitHistory(h, values), today, DayOfWeek.SUNDAY)
        assertEquals(7, chain.size)
        assertTrue(chain.all { it.start.dayOfWeek == DayOfWeek.SUNDAY && it.end == it.start.plusDays(6) })
        assertEquals(DayStatus.MET, chain[5].status)
        assertEquals(DayStatus.PARTIAL, chain.last().status)
        assertTrue(chain.last().current)
        assertEquals(DayStatus.MISSED, chain.first().status)
    }

    @Test fun weeklyTimesSumCountsAndGraceIsNeutral() {
        val h = count.copy(schedule = Schedule.timesPerWeek(3), createdOn = today.minusWeeks(1))
        val previous = HabitRules.weekStart(today, DayOfWeek.MONDAY).minusWeeks(1)
        val empty = HabitHistory(h, emptyMap())
        val grace = DetailPresentation.weekPoint(empty, previous, today, DayOfWeek.MONDAY)
        assertTrue(grace.grace)
        assertEquals(DayStatus.REST, grace.status)
        assertEquals(DayStatus.PENDING, DetailPresentation.weekPoint(empty, today, today, DayOfWeek.MONDAY).status)
        assertEquals(DayStatus.BEFORE_START, DetailPresentation.weekPoint(empty, previous.minusWeeks(1), today, DayOfWeek.MONDAY).status)
        assertEquals(DayStatus.FUTURE, DetailPresentation.weekPoint(empty, today.plusWeeks(1), today, DayOfWeek.MONDAY).status)
        val met = DetailPresentation.weekPoint(HabitHistory(h, mapOf(previous to 3)), previous, today, DayOfWeek.MONDAY)
        assertEquals(DayStatus.MET, met.status)
    }

    @Test fun timerContextHasOnlyRemainingAndQuietPosition() {
        assertEquals(TimerContext("25:00 left", "Session 1 of 2 · 25 min each"), DetailPresentation.timerContext(snapshot()))
        assertEquals(TimerContext("14:32 remaining · paused", "Session 2 of 2 · 25 min each"),
            DetailPresentation.timerContext(snapshot(value = 25 * 60, runs = listOf(
                TimerSession(1, timed.id, now, null, SessionState.PAUSED, 1, 1500,
                    remainingMs = 872_000, epochDay = today.toEpochDay()),
            ))))
        assertEquals("25:00 left", DetailPresentation.timerContext(snapshot(value = 25 * 60)).primary)
        assertEquals("Today complete", DetailPresentation.timerContext(snapshot(value = 50 * 60)).primary)
        assertEquals("Rest day", DetailPresentation.timerContext(snapshot(timed.copy(schedule = Schedule.weekdays(DayOfWeek.MONDAY)))).primary)
    }

    @Test fun completedDayWinsOverStoredRunAfterDurationEdit() {
        val run = TimerSession(1, timed.id, now.minusSeconds(8 * 60), null, SessionState.RUNNING, 1, 25 * 60, epochDay = today.toEpochDay())
        val context = DetailPresentation.timerContext(snapshot(timed.copy(dailyTarget = 10), value = 100 * 60, runs = listOf(run)))
        assertEquals("Today complete", context.primary)
        assertEquals("Session 2 of 2 · 10 min each", context.secondary)
    }

    @Test fun singleSessionContextOmitsRedundantPositionAndEach() {
        val habit = timed.copy(sessions = 1, dailyTarget = 60)
        assertEquals("1 session · 60 min", DetailPresentation.timerContext(snapshot(habit)).secondary)
        assertEquals("1 session · 60 min", DetailPresentation.timerContext(snapshot(habit, value = 3600)).secondary)
    }

    @Test fun completedHistoryRemovesPausedSessionContext() {
        val run = TimerSession(1, timed.id, now.minusSeconds(20 * 60), null, SessionState.PAUSED, 1, 25 * 60,
            remainingMs = 17 * 60_000, epochDay = today.toEpochDay())
        val t = snapshot(value = 50 * 60, runs = listOf(run))
        assertEquals(null, t.tileSessionProgress)
        assertEquals("Today complete", DetailPresentation.timerContext(t).primary)
    }

    @Test fun midnightDropsUnfinishedCountdownFromNewDaysPresentation() {
        val midnight = today.atStartOfDay(zone).toInstant()
        val current = midnight.plusSeconds(5 * 60)
        val run = TimerSession(1, timed.id, midnight.minusSeconds(5 * 60), null, SessionState.RUNNING, 1, 25 * 60,
            epochDay = today.minusDays(1).toEpochDay())
        val history = HistoryAssembler.assemble(listOf(timed), emptyList(), listOf(run), emptyMap(), today, zone, current).single()
        val t = SnapshotBuilder.habitToday(history, today, DayOfWeek.MONDAY, current)
        assertEquals(0L, t.value)
        assertEquals("25:00 left", DetailPresentation.timerContext(t).primary)
    }

    @Test fun closedStatisticsExcludeTodayAndRestDays() {
        val h = count.copy(schedule = Schedule.weekdays(DayOfWeek.FRIDAY))
        val history = HabitHistory(h, mapOf(today to 2, today.minusDays(1) to 2, today.minusDays(2) to 2))
        assertEquals(1, DetailPresentation.closedDaysMet(history, today))
        val avoid = count.copy(isNegative = true, dailyTarget = 0, createdOn = today.minusDays(2))
        assertEquals(2, DetailPresentation.closedDaysMet(HabitHistory(avoid, emptyMap()), today))
    }

    @Test fun homeAccessibilityActionsMatchRealHoldBehavior() {
        assertEquals("Log one completion", HabitLabels.holdAction(snapshot(count)))
        assertEquals("Log a slip", HabitLabels.holdAction(snapshot(count.copy(isNegative = true))))
        assertEquals("Start timer", HabitLabels.holdAction(snapshot()))
        assertEquals("Pause timer", HabitLabels.holdAction(snapshot().copy(timerRunning = true)))
        assertEquals(null, HabitLabels.holdAction(snapshot(count.copy(type = HabitType.STEPS))))
    }

    @Test fun compactScheduleDoesNotDuplicateTimerContext() {
        assertEquals("2× per day · every day", DetailPresentation.schedule(count))
        assertEquals("≤ 2 per day · every day", DetailPresentation.schedule(count.copy(isNegative = true)))
        assertEquals("2 steps · every day", DetailPresentation.schedule(count.copy(type = HabitType.STEPS)))
        assertEquals("3 times a week", DetailPresentation.schedule(count.copy(schedule = Schedule.timesPerWeek(3))))
        assertEquals("", DetailPresentation.schedule(timed))
    }

    @Test fun daySheetLeadsWithCompletionProgressFromCreditedTime() {
        val history = HabitHistory(timed, mapOf(today to 35 * 60))
        assertEquals("1 of 2 sessions complete", DetailPresentation.dayProgress(history, today, today))
        assertEquals("4 of 2 sessions complete", DetailPresentation.dayProgress(history.copy(values = mapOf(today to 100 * 60)), today, today))
        assertEquals("1 / 2 completions", DetailPresentation.dayProgress(HabitHistory(count, mapOf(today to 1)), today, today))
        val avoid = count.copy(isNegative = true, dailyTarget = 0)
        assertEquals("1 / 0 slips allowed", DetailPresentation.dayProgress(HabitHistory(avoid, mapOf(today to 1)), today, today))
        val weekly = count.copy(schedule = Schedule.timesPerWeek(4))
        assertEquals("2 completions", DetailPresentation.dayProgress(HabitHistory(weekly, mapOf(today to 2)), today, today))
    }

    @Test fun daySheetDoesNotInventMissingStepsOrPreTrackingProgress() {
        val steps = HabitHistory(count.copy(type = HabitType.STEPS), emptyMap())
        assertEquals("NO STEP DATA", DetailPresentation.dayProgress(steps, today, today))
        assertEquals("0 / 2 steps", DetailPresentation.dayProgress(steps.copy(values = mapOf(today to 0)), today, today))
        assertEquals("No history before tracking started",
            DetailPresentation.dayProgress(HabitHistory(count, emptyMap()), count.createdOn.minusDays(1), today))
    }

    @Test fun partialCreationWeekAndWeeklyMissingStepsStayTruthful() {
        val h = count.copy(schedule = Schedule.daysPerWeek(2), createdOn = today.minusWeeks(1))
        val previous = HabitRules.weekStart(today, DayOfWeek.MONDAY).minusWeeks(1)
        val partial = DetailPresentation.weekPoint(HabitHistory(h, mapOf(previous to 2)), previous, today, DayOfWeek.MONDAY)
        assertEquals(DayStatus.PARTIAL, partial.status)
        assertEquals("Partly done, creation week grace", partial.stateLabel)
        val missing = DetailPresentation.weekPoint(HabitHistory(h.copy(type = HabitType.STEPS), emptyMap()), today, today, DayOfWeek.MONDAY)
        assertTrue(missing.missingSteps)
        assertEquals("No step data", missing.stateLabel)
    }
}
