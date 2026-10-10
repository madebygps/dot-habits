package com.madebygps.dothabits.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class TimerSessionAccountingTest {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 10, 10)
    private val now = day.atTime(12, 0).atZone(zone).toInstant()
    private val habit = Habit(1, "Focus", "dot", HabitType.TIMED, 25, createdOn = day, sessions = 4)
    private val run = TimerSession(7, habit.id, now.minusSeconds(328), null, SessionState.RUNNING,
        1, 1500, epochDay = day.toEpochDay())
    private fun snapshot(h: Habit = habit, sessions: List<TimerSession> = listOf(run), entries: List<Entry> = emptyList(), today: LocalDate = day) =
        SnapshotBuilder.build(HistoryAssembler.assemble(listOf(h), entries, sessions, emptyMap(), today, zone, now),
            today, DayOfWeek.MONDAY, now)

    @Test fun unfinishedRunningAndPausedTimeNeverCountsAsHabitCredit() {
        for (s in listOf(run, run.copy(state = SessionState.PAUSED, remainingMs = 1_172_000))) {
            val snap = snapshot(sessions = listOf(s))
            assertEquals(0L, snap.habits.single().value)
            assertEquals(1172L, snap.activeTimer!!.sessionRemaining)
            assertEquals(328f / 1500, HabitTileState.from(snap.habits.single()).interiorFraction, .0001f)
            assertEquals(0f, snap.habits.single().fraction, 0f)
        }
    }

    @Test fun completedCreditIsWrittenOnceAndClosedSessionDoesNotDoubleCount() {
        val closed = run.copy(state = SessionState.CLOSED, end = now, remainingMs = 0)
        val credit = Entry(habitId = habit.id, date = day, amount = closed.limitSeconds, createdAt = now)
        assertEquals(0L, snapshot(sessions = listOf(closed)).habits.single().value)
        assertEquals(1500L, snapshot(sessions = listOf(closed), entries = listOf(credit)).habits.single().value)
    }

    @Test fun historyAndGoalEditsPreserveTimerOnlyBelowDailyGoal() {
        for (minutes in listOf(1, 10, 25, 60, 300)) {
            for (completed in listOf(0L, 1L, 4L, 12L)) {
                val edited = habit.copy(dailyTarget = minutes, sessions = 1)
                val credit = Entry(habitId = habit.id, date = day, amount = completed * edited.sessionSeconds, createdAt = now)
                val snap = snapshot(edited, entries = listOf(credit))
                val t = snap.habits.single()
                if (completed >= edited.sessions) {
                    assertEquals(completed * edited.sessionSeconds, t.value)
                    assertFalse(t.canControlTimer)
                    assertFalse(t.timerRunning)
                    assertNull(t.timerEndsAt)
                    assertNull(t.tileSessionProgress)
                    assertNull(snap.activeTimer)
                    assertNull(TimerProgress.from(t))
                    assertTrue(HabitTileState.from(t).solid)
                    assertEquals(HoldAction.NONE_ALREADY_DONE, CompletionPolicy.holdAction(t))
                    continue
                }
                assertEquals(run.start.plusSeconds(1500), t.timerEndsAt)
                assertEquals(1172L, t.sessionRemaining)
                assertEquals(1500L, t.tileSessionProgress!!.totalSeconds)
                assertEquals(t.sessionRemaining, snap.activeTimer!!.sessionRemaining)
                assertEquals(t.sessionRemaining, TimerProgress.from(t)!!.remainingSeconds)
                assertEquals(328f / 1500, t.tileSessionProgress!!.fraction, .0001f)
                assertFalse(HabitTileState.from(t).solid)
                assertEquals(328f / 1500, HabitTileState.from(t).interiorFraction, .0001f)
            }
        }
    }

    @Test fun midnightHidesUnfinishedButRetainsOriginalDaysCompletedCredit() {
        val credit = Entry(habitId = habit.id, date = day, amount = 1500, createdAt = now)
        val snap = snapshot(entries = listOf(credit), today = day.plusDays(1))
        assertEquals(0L, snap.habits.single().value)
        assertFalse(snap.habits.single().timerRunning)
        assertEquals(1500L, HistoryAssembler.assemble(listOf(habit), listOf(credit), emptyList(), emptyMap(),
            day.plusDays(1), zone, now).single().values[day])
    }

    @Test fun pausedSessionIsUnavailableAfterHistoryMarksDailyGoalDone() {
        val paused = run.copy(state = SessionState.PAUSED, remainingMs = 1_172_000)
        val credit = Entry(habitId = habit.id, date = day, amount = 6000, createdAt = now)
        val snap = snapshot(sessions = listOf(paused), entries = listOf(credit))
        assertNull(snap.activeTimer)
        assertFalse(snap.habits.single().timerPaused)
        assertFalse(snap.habits.single().canControlTimer)
        assertTrue(HabitTileState.from(snap.habits.single()).solid)
    }

    @Test fun goalCompletionDiscardsOnlyTodaysUnfinishedTimerForThatHabit() {
        for (state in listOf(SessionState.RUNNING, SessionState.PAUSED)) {
            val session = run.copy(state = state)
            assertFalse(CompletionPolicy.shouldDiscardTimer(habit, habit.dailyGoalUnits - 1, session, day))
            assertTrue(CompletionPolicy.shouldDiscardTimer(habit, habit.dailyGoalUnits, session, day))
            assertTrue(CompletionPolicy.shouldDiscardTimer(habit, habit.dailyGoalUnits + 1, session, day))
            assertFalse(CompletionPolicy.shouldDiscardTimer(habit, habit.dailyGoalUnits, session.copy(habitId = 2), day))
            assertFalse(CompletionPolicy.shouldDiscardTimer(habit, habit.dailyGoalUnits, session.copy(epochDay = day.minusDays(1).toEpochDay()), day))
        }
        assertFalse(CompletionPolicy.shouldDiscardTimer(habit, habit.dailyGoalUnits, run.copy(state = SessionState.CLOSED), day))
        assertFalse(CompletionPolicy.timerGoalMet(habit.copy(type = HabitType.COUNT), habit.dailyGoalUnits))
    }

    @Test fun reducingGoalClearsTimerOnlyWhenRecordedCreditMeetsNewGoal() {
        val completed = 1500L
        for (edited in listOf(habit.copy(sessions = 1), habit.copy(dailyTarget = 5))) {
            assertTrue(CompletionPolicy.shouldDiscardTimer(edited, completed, run, day))
        }
        assertFalse(CompletionPolicy.shouldDiscardTimer(habit, completed, run, day))
        assertFalse(CompletionPolicy.shouldDiscardTimer(habit.copy(dailyTarget = 10), completed, run, day))
    }

    @Test fun weeklyGoalMetOnOtherDaysDoesNotDiscardTodaysIncompleteTimer() {
        val weekly = habit.copy(schedule = Schedule.daysPerWeek(1), createdOn = day.minusDays(1))
        val credit = Entry(habitId = habit.id, date = day.minusDays(1), amount = habit.dailyGoalUnits, createdAt = now)
        val snap = snapshot(weekly, entries = listOf(credit))
        val t = snap.habits.single()
        assertEquals(TodayStatus.DONE, t.status)
        assertTrue(t.canControlTimer)
        assertTrue(t.timerRunning)
        assertEquals(habit.id, snap.activeTimer!!.habitId)
        assertFalse(CompletionPolicy.shouldDiscardTimer(weekly, t.value, run, day))
    }

    @Test fun completedHabitIsExcludedFromGlyphTimerSelectionAndChoices() {
        val other = habit.copy(id = 2, name = "Read")
        val paused = run.copy(state = SessionState.PAUSED, remainingMs = 1_172_000)
        val snap = SnapshotBuilder.build(
            listOf(
                HabitHistory(habit, mapOf(day to habit.dailyGoalUnits), listOf(paused)),
                HabitHistory(other, emptyMap()),
            ),
            day, DayOfWeek.MONDAY, now, preferredTimer = habit.id,
        )
        assertEquals(other.id, snap.activeTimer!!.habitId)
        assertEquals(listOf(other.id), snap.activeTimer!!.choices)
        assertEquals(HoldAction.NONE_ALREADY_DONE, CompletionPolicy.holdAction(snap.habits.first()))
    }

    @Test fun correctionBelowGoalAllowsFreshSessionWithoutRestoringDiscardedTime() {
        val snap = snapshot(entries = emptyList(), sessions = emptyList())
        assertTrue(snap.habits.single().canControlTimer)
        assertEquals(HoldAction.TOGGLE_TIMER, CompletionPolicy.holdAction(snap.habits.single()))
        val clock = TimerMath.ClockReading(now.toEpochMilli(), 100_000, run.bootCount)
        val plan = TimerMath.startSession(habit.id, habit.sessionSeconds, emptyList(), clock, zone)
        assertTrue(plan.updates.isEmpty())
        assertEquals(habit.sessionSeconds * 1000, plan.newSession!!.remainingMs)
        assertEquals(day.toEpochDay(), plan.newSession!!.epochDay)
    }

    @Test fun fullSessionCompletionMeetsGoalWithoutPartialExtraCredit() {
        val h = habit.copy(sessions = 1)
        val clock = TimerMath.ClockReading(run.start.plusSeconds(run.limitSeconds).toEpochMilli(), 0, run.bootCount)
        val change = TimerMath.settle(run, clock, zone)
        assertEquals(SessionState.CLOSED, change.session!!.state)
        assertEquals(h.dailyGoalUnits, change.credit!!.amount)
        val snap = snapshot(h, sessions = listOf(change.session!!), entries = listOf(change.credit!!))
        assertEquals(h.dailyGoalUnits, snap.habits.single().value)
        assertFalse(snap.habits.single().canControlTimer)
        assertNull(snap.activeTimer)
    }
}
