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

    @Test fun historyAndGoalEditsNeverAlterActiveCountdownOrProgress() {
        for (minutes in listOf(1, 10, 25, 60, 300)) {
            for (completed in listOf(0L, 1L, 4L, 12L)) {
                val edited = habit.copy(dailyTarget = minutes, sessions = 1)
                val credit = Entry(habitId = habit.id, date = day, amount = completed * edited.sessionSeconds, createdAt = now)
                val snap = snapshot(edited, entries = listOf(credit))
                val t = snap.habits.single()
                assertEquals(run.start.plusSeconds(1500), t.timerEndsAt)
                assertEquals(1172L, t.sessionRemaining)
                assertEquals(1500L, t.tileSessionProgress!!.totalSeconds)
                assertEquals(t.sessionRemaining, snap.activeTimer!!.sessionRemaining)
                assertEquals(t.sessionRemaining, TimerProgress.from(t)!!.remainingSeconds)
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

    @Test fun pausedSessionIsSelectableEvenAfterHistoryMarksDailyGoalDone() {
        val paused = run.copy(state = SessionState.PAUSED, remainingMs = 1_172_000)
        val credit = Entry(habitId = habit.id, date = day, amount = 6000, createdAt = now)
        val snap = snapshot(sessions = listOf(paused), entries = listOf(credit))
        assertEquals(habit.id, snap.activeTimer!!.habitId)
        assertFalse(snap.activeTimer!!.running)
        assertEquals(1172L, snap.activeTimer!!.sessionRemaining)
        assertFalse(HabitTileState.from(snap.habits.single()).solid)
    }
}
