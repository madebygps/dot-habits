package com.madebygps.dothabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimerMathTest {
    private val zone = ZoneId.of("Europe/London")
    private val day = LocalDate.of(2026, 6, 1)
    private fun at(h: Int, m: Int, d: LocalDate = day): Instant = LocalDateTime.of(d, java.time.LocalTime.of(h, m)).atZone(zone).toInstant()

    private fun closed(start: Instant, end: Instant, id: Long = 0) =
        TimerSession(id, 1, start, end, SessionState.CLOSED, end, 1)

    @Test fun pauseAndResumeAccumulateSessions() {
        val sessions = listOf(closed(at(8, 0), at(8, 20)), closed(at(12, 0), at(12, 25)))
        val running = TimerSession(3, 1, at(21, 0), null, SessionState.RUNNING, at(21, 0), 1)
        val now = at(21, 15)
        assertEquals((20 + 25 + 15) * 60L, TimerMath.secondsOnDay(sessions + running, day, zone, now))
    }

    @Test fun shortPauseResumeRunsRoundOnlyAfterDailyAccumulation() {
        val start = at(8, 0)
        val sessions = listOf(
            closed(start, start.plusMillis(600), 1),
            closed(start.plusSeconds(1), start.plusMillis(1600), 2),
            closed(start.plusSeconds(2), start.plusMillis(2600), 3),
        )
        assertEquals(1L, TimerMath.secondsOnDay(sessions, day, zone, at(9, 0)))
        assertEquals(mapOf(day to 1L), TimerMath.secondsByDay(sessions, zone, at(9, 0)))
    }

    @Test fun subsecondCreditRoundsIndependentlyOnEachSideOfMidnight() {
        val midnight = at(0, 0, day.plusDays(1))
        val sessions = listOf(
            closed(midnight.minusMillis(600), midnight.plusMillis(600), 1),
            closed(midnight.minusMillis(2000), midnight.minusMillis(1400), 2),
            closed(midnight.plusMillis(1000), midnight.plusMillis(1600), 3),
        )
        assertEquals(mapOf(day to 1L, day.plusDays(1) to 1L),
            TimerMath.secondsByDay(sessions, zone, midnight.plusSeconds(10)))
        for (date in listOf(day, day.plusDays(1))) {
            assertEquals(1L, TimerMath.secondsOnDay(sessions, date, zone, midnight.plusSeconds(10)))
        }
    }

    @Test fun glyphHourRoundingCarriesIntoTheNextHour() {
        assertEquals("2H00", TimerMath.formatGlyphCountdown(119 * 60 + 1))
        assertEquals("2H00", TimerMath.formatGlyphCountdown(120 * 60))
        assertEquals("2H01", TimerMath.formatGlyphCountdown(120 * 60 + 1))
    }

    @Test fun sessionAcrossMidnightIsSplitBetweenDays() {
        val s = closed(at(23, 30), at(0, 45, day.plusDays(1)))
        val byDay = TimerMath.secondsByDay(listOf(s), zone, at(9, 0, day.plusDays(1)))
        assertEquals(30 * 60L, byDay[day])
        assertEquals(45 * 60L, byDay[day.plusDays(1)])
    }

    @Test fun runningAcrossMidnightCreditsTodayOnlyWithTimeAfterMidnight() {
        val s = TimerSession(1, 1, at(23, 50), null, SessionState.RUNNING, at(23, 50), 1)
        val now = at(0, 10, day.plusDays(1))
        assertEquals(10 * 60L, TimerMath.secondsOnDay(listOf(s), day.plusDays(1), zone, now))
        assertEquals(10 * 60L, TimerMath.secondsOnDay(listOf(s), day, zone, now))
    }

    @Test fun needsReviewCountsOnlyToLastConfirmedAlive() {
        val s = TimerSession(1, 1, at(20, 0), null, SessionState.NEEDS_REVIEW, at(20, 40), 1)
        // Even many hours later the uncertain time is never added.
        assertEquals(40 * 60L, TimerMath.secondsOnDay(listOf(s), day, zone, at(23, 59)))
    }

    @Test fun dstDayUsesRealElapsedTime() {
        val dst = LocalDate.of(2026, 3, 29) // UK clocks go forward at 01:00
        val s = closed(LocalDateTime.of(dst, java.time.LocalTime.of(0, 30)).atZone(zone).toInstant(),
            LocalDateTime.of(dst, java.time.LocalTime.of(2, 30)).atZone(zone).toInstant())
        assertEquals(60 * 60L, TimerMath.secondsOnDay(listOf(s), dst, zone, Instant.MAX))
    }

    @Test fun sessionRemainingAndDone() {
        val s = 25 * 60L
        assertEquals(s, TimerMath.sessionRemaining(0, s))
        assertEquals(20 * 60L, TimerMath.sessionRemaining(30 * 60, s))
        // Exactly at a boundary the next run is a full session.
        assertEquals(s, TimerMath.sessionRemaining(50 * 60, s))
        assertEquals(2, TimerMath.sessionsDone(50 * 60, s, 4))
        assertEquals(4, TimerMath.sessionsDone(200 * 60, s, 4))
    }

    @Test fun runStopsItselfAtItsLimit() {
        val run = TimerSession(1, 1, at(9, 0), null, SessionState.RUNNING, at(9, 0), 1, limitSeconds = 25 * 60L)
        assertTrue(TimerMath.isLive(run, at(9, 24)))
        assertFalse(TimerMath.isLive(run, at(9, 25)))
        // Long after the limit only the session's 25 minutes count, even before it's persisted.
        assertEquals(25 * 60L, TimerMath.secondsOnDay(listOf(run), day, zone, at(11, 0)))
        // A reboot-interrupted run is capped by its limit too.
        val review = run.copy(state = SessionState.NEEDS_REVIEW, lastAlive = at(10, 0))
        assertEquals(25 * 60L, TimerMath.secondsOnDay(listOf(review), day, zone, at(11, 0)))
    }

    @Test fun runWithLimitAcrossMidnightSplitsAndStops() {
        val run = TimerSession(1, 1, at(23, 50), null, SessionState.RUNNING, at(23, 50), 1, limitSeconds = 25 * 60L)
        val byDay = TimerMath.secondsByDay(listOf(run), zone, at(1, 0, day.plusDays(1)))
        assertEquals(10 * 60L, byDay[day])
        assertEquals(15 * 60L, byDay[day.plusDays(1)])
    }

    @Test fun legacyRunWithoutLimitKeepsCounting() {
        val run = TimerSession(1, 1, at(9, 0), null, SessionState.RUNNING, at(9, 0), 1)
        assertTrue(TimerMath.isLive(run, at(23, 0)))
        assertEquals(14 * 3600L, TimerMath.secondsOnDay(listOf(run), day, zone, at(23, 0)))
    }

    @Test fun formatting() {
        assertEquals("45m", TimerMath.formatDuration(45 * 60))
        assertEquals("1h", TimerMath.formatDuration(3600))
        assertEquals("1h 5m", TimerMath.formatDuration(3900))
        assertEquals("25:00", TimerMath.formatGlyphCountdown(25 * 60))
        assertEquals("24:01", TimerMath.formatGlyphCountdown(24 * 60 + 1))
        assertEquals("0:59", TimerMath.formatGlyphCountdown(59))
        assertEquals("2H00", TimerMath.formatGlyphCountdown(120 * 60))
        assertEquals("99:59", TimerMath.formatGlyphCountdown(99 * 60 + 59))
        assertEquals("12:05", TimerMath.formatClock(12 * 60 + 5))
        assertEquals("1:00:00", TimerMath.formatClock(3600))
    }

    @Test fun wallClockJumpForwardDoesNotAddTime() {
        val startWall = at(21, 0).toEpochMilli()
        // 15 real minutes pass, but the user moves the clock forward 2 hours.
        val now = TimerMath.ClockReading(at(23, 15).toEpochMilli(), 1_000_000L + 15 * 60_000L, 1)
        val start = Instant.ofEpochMilli(TimerMath.rebasedStartMs(startWall, 1_000_000L, now))
        val running = TimerSession(1, 1, start, null, SessionState.RUNNING, start, 1)
        assertEquals(15 * 60L, TimerMath.secondsOnDay(listOf(running), day, zone, Instant.ofEpochMilli(now.wallMs)))
    }

    @Test fun wallClockJumpBackwardDoesNotLoseTime() {
        val startWall = at(21, 0).toEpochMilli()
        // 30 real minutes pass, network time correction moves the clock back 10 minutes.
        val now = TimerMath.ClockReading(at(21, 20).toEpochMilli(), 5_000L + 30 * 60_000L, 1)
        val start = Instant.ofEpochMilli(TimerMath.rebasedStartMs(startWall, 5_000L, now))
        val running = TimerSession(1, 1, start, null, SessionState.RUNNING, start, 1)
        assertEquals(30 * 60L, TimerMath.secondsOnDay(listOf(running), day, zone, Instant.ofEpochMilli(now.wallMs)))
    }

    @Test fun rebaseFallsBackToWallStartWithoutMonotonicStamp() {
        val now = TimerMath.ClockReading(at(22, 0).toEpochMilli(), 10L, 1)
        assertEquals(at(21, 0).toEpochMilli(), TimerMath.rebasedStartMs(at(21, 0).toEpochMilli(), null, now))
        assertEquals(at(21, 0).toEpochMilli(), TimerMath.rebasedStartMs(at(21, 0).toEpochMilli(), 99L, now))
    }

    @Test fun rebaseIsIdempotent() {
        val now = TimerMath.ClockReading(at(22, 0).toEpochMilli(), 3_600_000L, 1)
        val once = TimerMath.rebasedStartMs(at(19, 0).toEpochMilli(), 0L, now)
        assertEquals(once, TimerMath.rebasedStartMs(once, 0L, now))
        assertEquals(at(21, 0).toEpochMilli(), once)
    }
}
