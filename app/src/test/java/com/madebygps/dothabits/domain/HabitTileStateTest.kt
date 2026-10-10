package com.madebygps.dothabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HabitTileStateTest {
    private val day = LocalDate.of(2026, 10, 10)
    private val now = day.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC)
    private val count = Habit(1, "Water", "dot", HabitType.COUNT, 4, createdOn = day)
    private val timed = count.copy(type = HabitType.TIMED, dailyTarget = 20, sessions = 3)
    private val steps = count.copy(type = HabitType.STEPS, dailyTarget = 7000)
    private val avoid = count.copy(isNegative = true, dailyTarget = 1)

    private fun today(
        habit: Habit,
        value: Long = 0,
        sessions: List<TimerSession> = emptyList(),
        hasData: Boolean = true,
        history: Map<LocalDate, Long> = mapOf(day to value),
    ): HabitToday = SnapshotBuilder.habitToday(HabitHistory(habit, history, sessions, hasData), day, DayOfWeek.MONDAY, now)

    @Test fun countUsesOnlyWholeBorderUnitsUntilGoal() {
        val state = HabitTileState.from(today(count, 2))
        assertEquals(4, state.segments)
        assertEquals(.5f, state.borderFraction, 0f)
        assertEquals(0f, state.interiorFraction, 0f)
        assertFalse(state.solid)
        assertTrue(HabitTileState.from(today(count, 4)).solid)
    }

    @Test fun oneCountIsAnEmptyOutlineThenSolid() {
        val habit = count.copy(dailyTarget = 1)
        assertEquals(0, HabitTileState.from(today(habit)).segments)
        assertEquals(0f, HabitTileState.from(today(habit)).borderFraction, 0f)
        assertTrue(HabitTileState.from(today(habit, 1)).solid)
    }

    @Test fun countUsesWeeklyTotalForTimesPerWeek() {
        val habit = count.copy(dailyTarget = 1, schedule = Schedule.timesPerWeek(4))
        val state = HabitTileState.from(today(habit, history = mapOf(day.minusDays(1) to 1, day to 1)))
        assertEquals(4, state.segments)
        assertEquals(.5f, state.borderFraction, 0f)
        assertEquals(0f, state.interiorFraction, 0f)
    }

    @Test fun timerSeparatesFinishedSessionsFromPartialSession() {
        val t = today(timed, 28 * 60)
        val state = HabitTileState.from(t)
        assertEquals(3, state.segments)
        assertEquals(1f / 3, state.borderFraction, 0.0001f)
        assertEquals(.4f, state.interiorFraction, 0.0001f)
        assertEquals(state, HabitTileState.from(t.copy(timerRunning = true)))
    }

    @Test fun sessionBoundaryResetsInteriorButRetainsBorder() {
        val state = HabitTileState.from(today(timed, 40 * 60))
        assertEquals(2f / 3, state.borderFraction, 0.0001f)
        assertEquals(0f, state.interiorFraction, 0f)
        assertFalse(state.solid)
        assertTrue(HabitTileState.from(today(timed, 60 * 60)).solid)
    }

    @Test fun singleSessionTimerStillFillsInterior() {
        val state = HabitTileState.from(today(timed.copy(sessions = 1), 10 * 60))
        assertEquals(0, state.segments)
        assertEquals(0f, state.borderFraction, 0f)
        assertEquals(.5f, state.interiorFraction, 0f)
    }

    @Test fun stepsHaveContinuousBorderAndDailyInterior() {
        val state = HabitTileState.from(today(steps, 3500))
        assertEquals(0, state.segments)
        assertEquals(0f, state.borderFraction, 0f)
        assertEquals(.5f, state.interiorFraction, 0f)
        assertTrue(HabitTileState.from(today(steps, 7000)).solid)
    }

    @Test fun missingStepsAreNotShownAsCompletedOrEstimated() {
        val t = today(steps, 7000, hasData = false)
        val state = HabitTileState.from(t)
        assertFalse(state.solid)
        assertEquals(0f, state.interiorFraction, 0f)
        assertTrue(state.dimmed)
        assertEquals("NO STEP DATA", HabitLabels.caption(t))
        assertTrue(HabitLabels.accessibility(t).contains("NO STEP DATA"))
    }

    @Test fun avoidNeverFillsAnOngoingDay() {
        for (slips in 0L..2L) {
            val t = today(avoid, slips)
            val state = HabitTileState.from(t)
            assertTrue(state.dashed)
            assertFalse(state.solid)
            assertEquals(0f, state.interiorFraction, 0f)
            assertEquals(0f, state.borderFraction, 0f)
            assertEquals(slips > 1, state.dimmed)
            assertEquals("$slips/${avoid.dailyTarget} SLIPS" + if (slips > 1) " · SLIPPED" else "", HabitLabels.caption(t))
        }

        assertEquals("1 SLIP / 1 ALLOWED", HabitLabels.detail(today(avoid, 1)))
        assertEquals("0 SLIPS / 0 ALLOWED", HabitLabels.detail(today(avoid.copy(dailyTarget = 0))))
    }

    @Test fun accessibilityIncludesUnitsAndNotJustAStreak() {
        val description = HabitLabels.accessibility(today(count, 2))
        assertTrue(description.contains("Water"))
        assertTrue(description.contains("2/4 TODAY"))
        assertTrue(description.contains("in progress"))
    }

    @Test fun restDaysStayDimmedAndEmpty() {
        val habit = count.copy(schedule = Schedule.weekdays(DayOfWeek.MONDAY))
        val state = HabitTileState.from(today(habit))
        assertTrue(state.dimmed)
        assertFalse(state.solid)
        assertEquals(0f, state.borderFraction, 0f)
    }

    @Test fun completedDaysPerWeekGoalKeepsItsCompletionCue() {
        val habit = count.copy(dailyTarget = 1, schedule = Schedule.daysPerWeek(2))
        val t = today(habit, history = mapOf(day.minusDays(1) to 1, day.minusDays(2) to 1))
        assertEquals(TodayStatus.DONE, t.status)
        assertTrue(HabitTileState.from(t).solid)
    }

    @Test fun activeStoredRunDoesNotWrapAfterHistoryOrDurationEdits() {
        val start = now.minusSeconds(8 * 60)
        val run = TimerSession(1, timed.id, start, null, SessionState.RUNNING, now, 1, 20 * 60)
        for (target in listOf(10, 20)) {
            val t = today(timed.copy(dailyTarget = target), value = 60 * 60, sessions = listOf(run))
            val state = HabitTileState.from(t)
            assertFalse(state.solid)
            assertEquals(.4f, state.interiorFraction, .0001f)
            assertEquals("12:00 LEFT", HabitLabels.caption(t))
        }
    }

    @Test fun resumedRunRetainsEarlierSessionProgress() {
        val start = now.minusSeconds(3 * 60)
        val run = TimerSession(1, timed.id, start, null, SessionState.RUNNING, now, 1, 12 * 60)
        val t = today(timed, 11 * 60, listOf(run))
        assertEquals(.55f, HabitTileState.from(t).interiorFraction, .0001f)
        assertEquals(9 * 60L, t.tileSessionProgress?.remainingSeconds)
    }

    @Test fun midnightSplitsDailyAccountingNotLiveSessionFill() {
        val midnight = day.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
        val current = midnight.plusSeconds(5 * 60)
        val start = midnight.minusSeconds(5 * 60)
        val run = TimerSession(1, timed.id, start, null, SessionState.RUNNING, current, 1, 20 * 60)
        val history = HistoryAssembler.assemble(listOf(timed), emptyList(), listOf(run), emptyMap(), day, ZoneId.of("UTC"), current)
        val t = SnapshotBuilder.build(history, day, DayOfWeek.MONDAY, current).habits.single()
        assertEquals(5 * 60L, t.value)
        assertEquals(.5f, HabitTileState.from(t).interiorFraction, .0001f)
        assertEquals("10:00 LEFT", HabitLabels.caption(t))
    }

    @Test fun reviewUsesOnlyConfirmedAliveTimeAndNeverLooksDone() {
        val start = now.minusSeconds(20 * 60)
        val run = TimerSession(1, timed.id, start, null, SessionState.NEEDS_REVIEW, start.plusSeconds(8 * 60), 1, 20 * 60)
        val t = today(timed, 60 * 60, listOf(run))
        assertEquals("NEEDS REVIEW", HabitLabels.caption(t))
        assertFalse(HabitTileState.from(t).solid)
        assertEquals(.4f, HabitTileState.from(t).interiorFraction, .0001f)
    }

    @Test fun expiredRunFallsBackToSessionBoundary() {
        val start = now.minusSeconds(20 * 60)
        val run = TimerSession(1, timed.id, start, null, SessionState.RUNNING, start, 1, 20 * 60)
        val t = today(timed, 20 * 60, listOf(run))
        assertFalse(t.timerRunning)
        assertNull(t.tileSessionProgress)
        assertEquals(0f, HabitTileState.from(t).interiorFraction, 0f)
        assertEquals(1f / 3, HabitTileState.from(t).borderFraction, .0001f)
    }
}
