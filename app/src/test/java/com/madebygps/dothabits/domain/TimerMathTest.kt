package com.madebygps.dothabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun goalReachedAt() {
        val now = at(10, 0)
        assertEquals(at(10, 30), TimerMath.goalReachedAt(30 * 60, 60 * 60, now))
        assertNull(TimerMath.goalReachedAt(60 * 60, 60 * 60, now))
    }

    @Test fun formatting() {
        assertEquals("45m", TimerMath.formatDuration(45 * 60))
        assertEquals("1h", TimerMath.formatDuration(3600))
        assertEquals("1h 5m", TimerMath.formatDuration(3900))
        assertEquals("37", TimerMath.formatGlyphClock(37 * 60 + 59))
        assertEquals("1:45", TimerMath.formatGlyphClock(105 * 60))
    }
}
