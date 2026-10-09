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

    private fun today(value: Long, running: Boolean = true): HabitToday {
        val sessions = if (running) listOf(
            TimerSession(
                1, habit.id, now, null, SessionState.RUNNING, now, 1,
                limitSeconds = habit.sessionSeconds,
            ),
        ) else emptyList()
        return SnapshotBuilder.build(
            listOf(HabitHistory(habit, mapOf(day to value), sessions)),
            day, DayOfWeek.MONDAY, now,
        ).habits.single()
    }

    @Test fun newSessionStartsAtZero() {
        assertEquals(TimerProgress(0, 1_500), TimerProgress.from(today(0)))
    }

    @Test fun progressUsesCurrentSessionRatherThanDailyGoal() {
        assertEquals(TimerProgress(200, 1_200), TimerProgress.from(today(300)))
        assertEquals(TimerProgress(200, 1_200), TimerProgress.from(today(1_800)))
    }

    @Test fun resumedSessionKeepsAccumulatedProgress() {
        assertEquals(TimerProgress(500, 750), TimerProgress.from(today(2_250)))
    }

    @Test fun nextSessionResetsProgress() {
        assertEquals(TimerProgress(0, 1_500), TimerProgress.from(today(1_500)))
    }

    @Test fun finalSecondDoesNotClaimCompletionEarly() {
        assertEquals(TimerProgress(999, 1), TimerProgress.from(today(1_499)))
    }

    @Test fun pausedOrFinishedTimersHaveNoLiveProgress() {
        assertNull(TimerProgress.from(today(300, running = false)))
        assertNull(TimerProgress.from(today(6_000, running = false)))
    }

    @Test fun shorteningHabitDoesNotWrapAnExistingLongerRun() {
        val edited = today(1_600).copy(habit = habit.copy(dailyTarget = 5))
        assertEquals(TimerProgress(833, 300), TimerProgress.from(edited, 1_800, 300))
    }

    @Test fun storedRunRemainingWinsOverDailyModuloAfterMidnightOrHistoryEdit() {
        assertEquals(TimerProgress(800, 300), TimerProgress.from(today(0), 1_500, 300))
    }

    @Test fun updatesAdvanceWithoutRegressingForAnUnchangedRun() {
        val values = listOf(1_500L, 1_485L, 1_470L, 1_455L, 1L).map {
            requireNotNull(TimerProgress.from(today(0), 1_500, it)).elapsed
        }
        assertEquals(listOf(0, 10, 20, 30, 999), values)
    }
}
