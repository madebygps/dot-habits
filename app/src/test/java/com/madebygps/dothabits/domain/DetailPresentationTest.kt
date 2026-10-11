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

    @Test fun pastDailyRunsRemainSeparateAcrossMissedAndPartialDays() {
        val history = HabitHistory(count, mapOf(
            today.minusDays(6) to 2L, today.minusDays(5) to 2L,
            today.minusDays(4) to 1L, today.minusDays(2) to 2L, today.minusDays(1) to 2L,
        ))
        val runs = DetailPresentation.calendarStreaks(history, today, DayOfWeek.MONDAY)
        assertEquals(listOf(2, 2), runs.map { it.periods.size })
        assertTrue(runs.none { it.dayLinks(today.minusDays(4)).let { links -> links.first || links.second } })
        assertEquals(DetailPresentation.calendarStreak(history, today, DayOfWeek.MONDAY), runs.last())
        val broken = history.copy(values = history.values + (today to 1L))
        assertEquals(runs, DetailPresentation.calendarStreaks(broken, today, DayOfWeek.MONDAY))
    }

    @Test fun pastWeeklyRunsBreakOnFailedClosedWeeksForEitherWeekStart() {
        for (firstDay in listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)) {
            val start = HabitRules.weekStart(today, firstDay)
            val habit = count.copy(schedule = Schedule.timesPerWeek(3), createdOn = start.minusWeeks(6))
            val history = HabitHistory(habit, mapOf(
                start.minusWeeks(5) to 3L, start.minusWeeks(4) to 3L,
                start.minusWeeks(2) to 3L, start.minusWeeks(1) to 3L,
            ))
            val runs = DetailPresentation.calendarStreaks(history, today, firstDay)
            assertEquals(listOf(2, 2), runs.map { it.periods.size })
            assertTrue(runs.none { it.connects(start.minusWeeks(4), start.minusWeeks(2)) })
            assertEquals(DetailPresentation.calendarStreak(history, today, firstDay), runs.last())
        }
    }

    @Test fun pastRunsBridgeRestDaysAndRemainAfterCurrentAvoidStreakBreaks() {
        val habit = count.copy(schedule = Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        val history = HabitHistory(habit, mapOf(today.minusDays(5) to 2L, today.minusDays(1) to 2L))
        val runs = DetailPresentation.calendarStreaks(history, today, DayOfWeek.MONDAY)
        assertEquals(true to true, runs.single().dayLinks(today.minusDays(3)))
        val avoid = count.copy(isNegative = true, dailyTarget = 0, createdOn = today.minusDays(3))
        val slipped = HabitHistory(avoid, mapOf(today to 1L))
        assertEquals(3, DetailPresentation.calendarStreaks(slipped, today, DayOfWeek.MONDAY).single().periods.size)
        assertEquals(null, DetailPresentation.calendarStreak(slipped, today, DayOfWeek.MONDAY).latest)
    }

    @Test fun heroProgressShowsCountsSlipsAndWeeklyTotalsWithoutDuplicateDetails() {
        assertEquals(HeroProgress("1/2", "TODAY"), DetailPresentation.heroProgress(snapshot(count, 1)))
        assertEquals(HeroProgress("0/1", "SLIPS"), DetailPresentation.heroProgress(snapshot(count.copy(isNegative = true, dailyTarget = 1))))
        val weekly = count.copy(schedule = Schedule.timesPerWeek(3))
        assertEquals(HeroProgress("2/3", "THIS WEEK"), DetailPresentation.heroProgress(snapshot(weekly, 2)))
        val rest = count.copy(schedule = Schedule.weekdays(DayOfWeek.MONDAY))
        assertEquals(HeroProgress("0/2", "REST DAY"), DetailPresentation.heroProgress(snapshot(rest)))
    }

    @Test fun heroTimerShowsCountdownAndCompletedSessionsInsideTile() {
        assertEquals(HeroProgress("25:00", "LEFT · 0/2"), DetailPresentation.heroProgress(snapshot()))
        assertEquals(HeroProgress("2/2", "SESSIONS"), DetailPresentation.heroProgress(snapshot(value = 50 * 60)))
        val paused = TimerSession(1, timed.id, now, null, SessionState.PAUSED, 1, 1500,
            remainingMs = 872_000, epochDay = today.toEpochDay())
        assertEquals(HeroProgress("14:32", "PAUSED · 1/2"),
            DetailPresentation.heroProgress(snapshot(value = 25 * 60, runs = listOf(paused))))
    }

    @Test fun heroStepsDistinguishMissingDataFromMeasuredZero() {
        val steps = count.copy(type = HabitType.STEPS)
        assertEquals(HeroProgress("0", "STEPS"), DetailPresentation.heroProgress(snapshot(steps)))
        val missing = snapshot(steps).copy(hasData = false)
        assertEquals(HeroProgress("--", "NO STEP DATA"), DetailPresentation.heroProgress(missing))
    }

    @Test fun calendarFlameMarksLastSuccessfulPeriodNotPendingToday() {
        val streak = DetailPresentation.calendarStreak(
            HabitHistory(count, mapOf(today.minusDays(1) to 2L)), today, DayOfWeek.MONDAY,
        )
        assertEquals(today.minusDays(1), streak.latest?.start)
        assertEquals(null, DetailPresentation.calendarStreak(HabitHistory(count, emptyMap()), today, DayOfWeek.MONDAY).latest)
    }

    @Test fun calendarLinksOnlyCurrentRunAndLeavesUnmetTodayPending() {
        val values = mapOf(today.minusDays(5) to 2L, today.minusDays(3) to 2L, today.minusDays(2) to 2L, today.minusDays(1) to 2L)
        val streak = DetailPresentation.calendarStreak(HabitHistory(count, values), today, DayOfWeek.MONDAY)
        assertEquals((3 downTo 1).map { today.minusDays(it.toLong()) }, streak.periods.map { it.start })
        assertFalse(streak.contains(today))
        assertEquals(false to true, streak.dayLinks(today.minusDays(3)))
        assertEquals(true to true, streak.dayLinks(today.minusDays(2)))
        assertEquals(true to false, streak.dayLinks(today.minusDays(1)))
        assertEquals(false to false, streak.dayLinks(today))
        assertFalse(streak.contains(today.minusDays(5)))
    }

    @Test fun calendarBridgesRestDaysWithoutCountingOffScheduleCompletions() {
        val habit = count.copy(schedule = Schedule.weekdays(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        val monday = today.minusDays(5)
        val friday = today.minusDays(1)
        val streak = DetailPresentation.calendarStreak(
            HabitHistory(habit, mapOf(monday to 2L, monday.plusDays(1) to 2L, friday to 2L)), today, DayOfWeek.MONDAY,
        )
        assertEquals(listOf(monday, friday), streak.periods.map { it.start })
        assertEquals(true to true, streak.dayLinks(monday.plusDays(2)))
        assertFalse(streak.contains(monday.plusDays(1)))
        assertEquals(false to false, streak.dayLinks(today))
    }

    @Test fun calendarSupportsLongBackfilledStreaksAcrossMonthBoundaries() {
        val values = (0L..45L).associate { today.minusDays(it) to 2L }
        val streak = DetailPresentation.calendarStreak(HabitHistory(count, values), today, DayOfWeek.MONDAY)
        assertEquals(46, streak.periods.size)
        assertEquals(today.minusDays(45), streak.periods.first().start)
        assertEquals(today, streak.periods.last().start)
    }

    @Test fun calendarAvoidStreakExcludesOpenTodayAndDisappearsOnSlip() {
        val avoid = count.copy(isNegative = true, dailyTarget = 0, createdOn = today.minusDays(3))
        val history = HabitHistory(avoid, emptyMap())
        val streak = DetailPresentation.calendarStreak(history, today, DayOfWeek.MONDAY)
        assertEquals(3, streak.periods.size)
        assertFalse(streak.contains(today))
        assertTrue(DetailPresentation.calendarStreak(history.copy(values = mapOf(today to 1L)), today, DayOfWeek.MONDAY).periods.isEmpty())
    }

    @Test fun calendarWeeklyLinksUseSuccessfulWeeksNotIndividualDays() {
        for (firstDay in listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)) {
            val habit = count.copy(schedule = Schedule.timesPerWeek(3), createdOn = today.minusWeeks(8))
            val current = HabitRules.weekStart(today, firstDay)
            val history = HabitHistory(habit, mapOf(current.minusWeeks(2) to 3L, current.minusWeeks(1) to 3L, current to 1L))
            val streak = DetailPresentation.calendarStreak(history, today, firstDay)
            assertEquals(listOf(current.minusWeeks(2), current.minusWeeks(1)), streak.periods.map { it.start })
            assertTrue(streak.connects(current.minusWeeks(2), current.minusWeeks(1)))
            assertFalse(streak.connects(current.minusWeeks(1), current))
            assertFalse(streak.contains(today))
            val met = DetailPresentation.calendarStreak(history.copy(values = history.values + (current to 3L)), today, firstDay)
            assertEquals(3, met.periods.size)
            assertTrue(met.contains(today))
        }
    }

    @Test fun calendarDoesNotInventMissingStepLinksOrCountPartialDays() {
        val steps = HabitHistory(count.copy(type = HabitType.STEPS), mapOf(today.minusDays(1) to 2L))
        val streak = DetailPresentation.calendarStreak(steps, today, DayOfWeek.MONDAY)
        assertEquals(listOf(today.minusDays(1)), streak.periods.map { it.start })
        val partial = HabitHistory(count, mapOf(today.minusDays(1) to 1L))
        assertTrue(DetailPresentation.calendarStreak(partial, today, DayOfWeek.MONDAY).periods.isEmpty())
    }

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

    @Test fun timerContextShowsRemainingAndConfiguredGoal() {
        assertEquals(TimerContext("25:00 left", "2 sessions of 25 min every day"), DetailPresentation.timerContext(snapshot()))
        assertEquals(TimerContext("14:32 remaining · paused", "2 sessions of 25 min every day"),
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
        assertEquals("2 sessions of 10 min every day", context.secondary)
    }

    @Test fun singleSessionContextOmitsRedundantPositionAndEach() {
        val habit = timed.copy(sessions = 1, dailyTarget = 60)
        assertEquals("1 session of 60 min every day", DetailPresentation.timerContext(snapshot(habit)).secondary)
        assertEquals("1 session of 60 min every day", DetailPresentation.timerContext(snapshot(habit, value = 3600)).secondary)
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
        assertEquals("2 sessions of 25 min every day", DetailPresentation.schedule(timed))
    }

    @Test fun timerGoalIncludesSelectedWeekdaysOrDistinctDaysPerWeek() {
        val weekdays = timed.copy(schedule = Schedule.weekdays(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))
        assertEquals("2 sessions of 25 min on Mon Fri", DetailPresentation.schedule(weekdays))
        val weekly = timed.copy(schedule = Schedule.daysPerWeek(3))
        assertEquals("2 sessions of 25 min per day, 3 days a week", DetailPresentation.schedule(weekly))
        assertEquals(DetailPresentation.schedule(weekly), DetailPresentation.timerContext(snapshot(weekly)).secondary)
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
