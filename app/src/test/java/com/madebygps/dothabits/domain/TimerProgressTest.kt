package com.madebygps.dothabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class TimerProgressTest {
    private val day = LocalDate.of(2026, 10, 9)
    private val now = day.atTime(12, 0).toInstant(ZoneOffset.UTC)
    private val habit = Habit(1, "Focus", "dot", HabitType.TIMED, 25, sessions = 4, createdOn = day)

    private fun today(value: Long, running: Boolean = true, remaining: Long = 1500): HabitToday {
        val sessions = if (running) listOf(
            TimerSession(
                1, habit.id, now, null, SessionState.RUNNING, 1,
                limitSeconds = habit.sessionSeconds, remainingMs = remaining * 1000, epochDay = day.toEpochDay(),
            ),
        ) else emptyList()
        return SnapshotBuilder.build(
            listOf(HabitHistory(habit, mapOf(day to value), sessions)),
            day, DayOfWeek.MONDAY, now,
        ).habits.single()
    }

    @Test fun newSessionStartsAtZero() {
        assertEquals(TimerProgress(0, 1_500, 1_500), TimerProgress.from(today(0)))
    }

    @Test fun progressUsesCurrentSessionRatherThanDailyGoal() {
        assertEquals(TimerProgress(300, 1_500, 1_200), TimerProgress.from(today(0, remaining = 1200)))
        assertEquals(TimerProgress(300, 1_500, 1_200), TimerProgress.from(today(1500, remaining = 1200)))
    }

    @Test fun resumedSessionKeepsAccumulatedProgress() {
        assertEquals(TimerProgress(750, 1_500, 750), TimerProgress.from(today(1500, remaining = 750)))
    }

    @Test fun nextSessionResetsProgress() {
        assertEquals(TimerProgress(0, 1_500, 1_500), TimerProgress.from(today(1_500)))
    }

    @Test fun finalSecondDoesNotClaimCompletionEarly() {
        assertEquals(TimerProgress(1_499, 1_500, 1), TimerProgress.from(today(0, remaining = 1)))
    }

    @Test fun pausedOrFinishedTimersHaveNoLiveProgress() {
        assertNull(TimerProgress.from(today(300, running = false)))
        assertNull(TimerProgress.from(today(6_000, running = false)))
    }

    @Test fun shorteningHabitDoesNotWrapAnExistingLongerRun() {
        val edited = today(1_600).copy(habit = habit.copy(dailyTarget = 5))
        assertEquals(TimerProgress(1_500, 1_800, 300), TimerProgress.from(edited, 1_800, 300))
    }

    @Test fun habitDurationEditsPreserveStoredSessionMaximum() {
        val running = today(0, remaining = 300)
        for (minutes in listOf(5, 60)) {
            val edited = running.copy(habit = habit.copy(dailyTarget = minutes))
            assertEquals(TimerProgress(1_200, 1_500, 300), TimerProgress.from(edited))
        }
    }

    @Test fun hourSessionUsesSecondsWithoutNormalization() {
        assertEquals(TimerProgress(900, 3_600, 2_700), TimerProgress.from(today(0), 3_600, 2_700))
    }

    @Test fun remainingSecondsStayWithinSessionBounds() {
        assertEquals(TimerProgress(0, 1_500, 1_500), TimerProgress.from(today(0), 1_500, 1_600))
        assertEquals(TimerProgress(1_500, 1_500, 0), TimerProgress.from(today(0), 1_500, -1))
    }

    @Test fun storedRunRemainingWinsOverDailyModuloAfterMidnightOrHistoryEdit() {
        assertEquals(TimerProgress(1_200, 1_500, 300), TimerProgress.from(today(0), 1_500, 300))
    }

    @Test fun updatesAdvanceWithoutRegressingForAnUnchangedRun() {
        val values = listOf(1_500L, 1_485L, 1_470L, 1_455L, 1L).map {
            requireNotNull(TimerProgress.from(today(0), 1_500, it)).elapsedSeconds
        }
        assertEquals(listOf(0, 15, 30, 45, 1_499), values)
    }
}
