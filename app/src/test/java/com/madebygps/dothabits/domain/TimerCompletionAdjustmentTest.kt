package com.madebygps.dothabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TimerCompletionAdjustmentTest {
    private val day = LocalDate.of(2026, 10, 10)
    private val zone = ZoneId.of("UTC")
    private val now = day.atTime(12, 0).atZone(zone).toInstant()
    private val habit = Habit(1, "Read", "book", HabitType.TIMED, 25, sessions = 4, createdOn = day.minusDays(2))
    private val length = habit.sessionSeconds

    private fun entry(date: LocalDate, amount: Long) = Entry(1, habit.id, date, amount, now)

    private fun closed(seconds: Long, date: LocalDate = day): TimerSession {
        val end = date.atTime(10, 0).atZone(zone).toInstant()
        return TimerSession(1, habit.id, end.minusSeconds(seconds), end, SessionState.CLOSED, end, 1)
    }

    private fun history(
        runs: List<TimerSession>,
        entries: List<Entry> = emptyList(),
        at: Instant = now,
        h: Habit = habit,
    ) = HistoryAssembler.assemble(listOf(h), entries, runs, emptyMap(), at.atZone(zone).toLocalDate(), zone, at).single()

    private fun correction(
        runs: List<TimerSession>,
        count: Long,
        manual: Long = 0,
        date: LocalDate = day,
        at: Instant = now,
        h: Habit = habit,
    ): Entry = entry(date, TimerMath.completionAdjustment(
        TimerMath.secondsOnDay(runs, date, zone, at), manual, h.sessionSeconds, count,
    ))

    @Test fun absoluteZeroOneAndNOverrideRecordedCreditBothWaysWithoutDeletingRuns() {
        for (recordedCount in listOf(0L, 1L, 4L, 7L)) {
            val runs = listOf(closed(recordedCount * length))
            for (desired in listOf(0L, 1L, 4L, 7L)) {
                val adjustment = correction(runs, desired)
                val result = history(runs, listOf(adjustment))
                assertEquals(desired * length, result.values[day])
                assertEquals(runs, result.sessions)
                assertEquals((desired - recordedCount) * length, adjustment.amount)
                assertEquals("$desired of 4 sessions complete", DetailPresentation.dayProgress(result, day, day))
            }
        }
    }

    @Test fun fractionsAndLegacyManualCreditArePreservedWithIdempotentReplacement() {
        val runs = listOf(closed(3 * length + 328))
        for (oldManual in listOf(0L, 777L, -length, -3 * length)) {
            val original = 3 * length + 328 + oldManual
            var manual = oldManual
            for (desired in listOf(0L, 1L, 4L, 4L, 0L, 1L, 1L)) {
                val adjusted = correction(runs, desired, manual)
                assertEquals(desired * length + original % length, history(runs, listOf(adjusted)).values[day])
                assertEquals(adjusted.amount, correction(runs, desired, adjusted.amount).amount)
                manual = adjusted.amount
            }
        }
    }

    @Test fun existingOvercorrectionCannotClampAwayANewDesiredCount() {
        val runs = listOf(closed(length))
        val old = -5 * length
        assertEquals(0L, history(runs, listOf(entry(day, old))).values[day])
        for (count in listOf(0L, 1L, 4L)) {
            val adjusted = correction(runs, count, old)
            assertEquals(count * length, history(runs, listOf(adjusted)).values[day])
        }
    }

    @Test fun multipleEntriesAreSummedThenReplacedNotAddedOrDoubleCounted() {
        val runs = listOf(closed(2 * length + 100))
        val existing = listOf(entry(day, 900), entry(day, -300), entry(day.minusDays(1), 88))
        val manual = existing.filter { it.date == day }.sumOf { it.amount }
        val adjusted = correction(runs, 1, manual)
        val replaced = existing.filter { it.date != day } + adjusted
        val result = history(runs, replaced)
        assertEquals(length + 700, result.values[day])
        assertEquals(88L, result.values[day.minusDays(1)])
    }

    @Test fun runningCorrectionsRetainPartialProgressCountdownAndStoredDeadlineEvenWhenGoalMet() {
        val run = TimerSession(2, habit.id, now.minusSeconds(328), null, SessionState.RUNNING, now, 1, length)
        val runs = listOf(closed(2 * length), run)
        val original = SnapshotBuilder.build(listOf(history(runs)), day, DayOfWeek.MONDAY, now)
        for (count in listOf(0L, 1L, 4L, 7L)) {
            val adjusted = correction(runs, count)
            val result = history(runs, listOf(adjusted))
            val snapshot = SnapshotBuilder.build(listOf(result), day, DayOfWeek.MONDAY, now)
            val today = snapshot.habits.single()
            assertEquals(count * length + 328, today.value)
            assertTrue(today.timerRunning)
            assertEquals(TimerMath.limitEnd(run), today.timerEndsAt)
            assertEquals(length - 328, today.sessionRemaining)
            assertEquals(original.habits.single().tileSessionProgress, today.tileSessionProgress)
            assertEquals(original.activeTimer!!.sessionRemaining, snapshot.activeTimer!!.sessionRemaining)
            assertEquals(today.timerEndsAt, snapshot.activeTimer.endsAt)
            assertEquals(today.sessionRemaining, TimerProgress.from(today, run.limitSeconds!!)!!.remainingSeconds)
            assertEquals(original.activeTimer.habitId, snapshot.activeTimer.habitId)
            assertEquals(HoldAction.TOGGLE_TIMER, CompletionPolicy.holdAction(today))
            assertEquals(original.habits.single().tileSessionProgress!!.fraction, HabitTileState.from(today).interiorFraction)
            assertEquals(TimerMath.limitEnd(run), TimerMath.limitEnd(result.sessions.last()))
            val later = now.plusSeconds(60)
            assertEquals(count * length + 388, history(runs, listOf(adjusted), later).values[day])
            val end = TimerMath.limitEnd(run)
            assertEquals((count + 1) * length, history(runs, listOf(adjusted), end).values[day])
            assertFalse(SnapshotBuilder.habitToday(history(runs, listOf(adjusted), end), day, DayOfWeek.MONDAY, end).timerRunning)
        }
    }

    @Test fun midnightCorrectionsAffectOnlySelectedDayAndDoNotChangeRunLimit() {
        val midnight = day.atStartOfDay(zone).toInstant()
        val run = TimerSession(2, habit.id, midnight.minusSeconds(600), null, SessionState.RUNNING, midnight, 1, length)
        val at = midnight.plusSeconds(300)
        val runs = listOf(run)
        val previous = correction(runs, 1, date = day.minusDays(1), at = at)
        val current = correction(runs, 0, at = at)
        val result = history(runs, listOf(previous, current), at)
        assertEquals(length + 600, result.values[day.minusDays(1)])
        assertEquals(300L, result.values[day])
        val snapshot = SnapshotBuilder.build(listOf(result), day, DayOfWeek.MONDAY, at)
        val today = snapshot.habits.single()
        assertEquals(600L, today.tileSessionProgress!!.remainingSeconds)
        assertEquals(600L, today.sessionRemaining)
        assertEquals(TimerMath.limitEnd(run), today.timerEndsAt)
        assertEquals(today.sessionRemaining, snapshot.activeTimer!!.sessionRemaining)
        assertEquals(today.timerEndsAt, snapshot.activeTimer.endsAt)
        val finished = history(runs, listOf(previous, current), midnight.plusSeconds(3600))
        assertEquals(length + 600, finished.values[day.minusDays(1)])
        assertEquals(900L, finished.values[day])
        assertEquals(runs, finished.sessions)
    }

    @Test fun reviewCorrectionsUseOnlyConfirmedCreditAndKeepReviewProminent() {
        val run = TimerSession(2, habit.id, now.minusSeconds(800), null, SessionState.NEEDS_REVIEW, now.minusSeconds(472), 1, length)
        val runs = listOf(closed(2 * length), run)
        val adjusted = correction(runs, 4)
        val result = history(runs, listOf(adjusted), now.plusSeconds(3600))
        assertEquals(4 * length + 328, result.values[day])
        val today = SnapshotBuilder.habitToday(result, day, DayOfWeek.MONDAY, now)
        assertTrue(today.needsReview)
        assertFalse(today.timerRunning)
        assertEquals("Needs review", DetailPresentation.timerContext(today).primary)
        assertEquals(runs, result.sessions)
    }

    @Test fun durationEditsKeepSecondsCreditAndActiveLimitAndCanBeCorrectedAgain() {
        val run = TimerSession(2, habit.id, now.minusSeconds(328), null, SessionState.RUNNING, now, 1, length)
        val runs = listOf(closed(2 * length), run)
        val adjusted = correction(runs, 1)
        val shorter = habit.copy(dailyTarget = 10)
        val longer = habit.copy(dailyTarget = 60)
        for (edited in listOf(shorter, longer)) {
            val before = history(runs, listOf(adjusted), h = edited)
            assertEquals(length + 328, before.values[day])
            assertEquals("${(length + 328) / edited.sessionSeconds} of 4 sessions complete",
                DetailPresentation.dayProgress(before, day, day))
            for (count in listOf(0L, 1L, 4L)) {
                val next = correction(runs, count, adjusted.amount, h = edited)
                val after = history(runs, listOf(next), h = edited)
                assertEquals(count * edited.sessionSeconds + (length + 328) % edited.sessionSeconds, after.values[day])
                val today = SnapshotBuilder.habitToday(after, day, DayOfWeek.MONDAY, now)
                assertTrue(today.timerRunning)
                assertEquals(length - 328, today.tileSessionProgress!!.remainingSeconds)
                assertEquals(length - 328, today.sessionRemaining)
                assertEquals(TimerMath.limitEnd(run), today.timerEndsAt)
                assertEquals(length - 328, TimerProgress.from(today, run.limitSeconds!!)!!.remainingSeconds)
                assertEquals("19:32 left", DetailPresentation.timerContext(today).primary)
                assertEquals(TimerMath.limitEnd(run), TimerMath.limitEnd(after.sessions.last()))
            }
        }
    }

    @Test fun correctedHistoryFeedsStatusStreaksStatisticsAndSnapshotSurfaces() {
        val yesterday = day.minusDays(1)
        val runs = listOf(closed(4 * length, yesterday))
        for (count in listOf(0L, 1L, 4L)) {
            val adjusted = correction(runs, count, date = yesterday)
            val result = history(runs, listOf(adjusted))
            val met = count == 4L
            assertEquals(if (met) DayStatus.MET else if (count == 0L) DayStatus.MISSED else DayStatus.PARTIAL,
                DetailPresentation.dayPoint(result, yesterday, day).status)
            assertEquals(if (met) 1 else 0, DetailPresentation.closedDaysMet(result, day))
            assertEquals(if (met) 1 else 0, Stats.compute(listOf(result), day, DayOfWeek.MONDAY, StatsRange.ALL).overall.met)
            assertEquals(if (met) 1 else 0, HabitRules.streaks(habit, day, DayOfWeek.MONDAY, result.values).current)
            val snapshot = SnapshotBuilder.build(listOf(result), yesterday, DayOfWeek.MONDAY, now)
            assertEquals(count * length, snapshot.glyphHabit(habit.id)!!.value)
            assertEquals(met, snapshot.habits.single().isComplete)
            assertEquals(met, HabitTileState.from(snapshot.habits.single()).solid)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeCompletionCountIsRejected() {
        TimerMath.completionAdjustment(0, 0, length, -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroSessionLengthIsRejected() {
        TimerMath.completionAdjustment(0, 0, 0, 1)
    }
}
