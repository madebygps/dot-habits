package com.madebygps.dothabits.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TimerMathTest {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 10, 10)
    private val start = day.atTime(12, 0).atZone(zone).toInstant()
    private fun clock(seconds: Long = 0, boot: Int = 1, wallShift: Long = 0) =
        TimerMath.ClockReading(start.toEpochMilli() + seconds * 1000 + wallShift, 10_000 + seconds * 1000, boot)
    private fun session(duration: Long = 1500) = TimerSession(
        id = 7, habitId = 1, start = start, end = null, state = SessionState.RUNNING,
        bootCount = 1, limitSeconds = duration, epochDay = day.toEpochDay(), startElapsedMs = 10_000,
    )

    @Test fun pausesKeepMillisecondsAndResumeTheSamePlannedSession() {
        val s = session()
        val paused = TimerMath.pause(s, clock(300).copy(elapsedMs = 310_123))
        assertEquals(1_199_877L, paused.remainingMs)
        assertEquals(1200L, TimerMath.timing(paused, clock(600)).remainingSeconds)
        assertEquals(TimerMath.Transition.KEEP, TimerMath.transition(paused, clock(3600), zone))
        val resumed = TimerMath.resume(paused, clock(3600))
        assertEquals(s.id, resumed.id)
        assertEquals(s.epochDay, resumed.epochDay)
        assertEquals(s.limitSeconds, resumed.limitSeconds)
        assertEquals(TimerMath.Transition.KEEP, TimerMath.transition(resumed, clock(4799), zone))
        assertEquals(TimerMath.Transition.COMPLETE, TimerMath.transition(resumed, clock(4800), zone))
        assertNull(TimerMath.settle(paused, clock(3600), zone).credit)
        val finished = TimerMath.settle(resumed, clock(4800), zone)
        assertEquals(s.limitSeconds, finished.credit!!.amount)
        assertEquals(day, finished.credit.date)
        assertEquals(SessionState.CLOSED, finished.session!!.state)
        assertNull(TimerMath.settle(finished.session, clock(9000), zone).credit)
    }

    @Test fun manyShortPausesNeverLoseFractionalTime() {
        var s = session(60)
        var c = clock()
        repeat(100) {
            c = c.copy(wallMs = c.wallMs + 123, elapsedMs = c.elapsedMs + 123)
            s = TimerMath.resume(TimerMath.pause(s, c), c)
        }
        assertEquals(47_700L, s.remainingMs)
        assertEquals(60L, s.limitSeconds)
    }

    @Test fun countdownUsesMonotonicTimeDespiteWallClockChanges() {
        val s = session()
        for (shift in listOf(-3_600_000L, 0L, 3_600_000L)) {
            val c = clock(328, wallShift = shift)
            val time = TimerMath.timing(s, c)
            assertEquals(1172L, time.remainingSeconds)
            assertEquals(c.wallMs + 1_172_000, time.endsAt!!.toEpochMilli())
            assertEquals(time, TimerMath.timing(TimerMath.project(s, c), Instant.ofEpochMilli(c.wallMs)))
        }
    }

    @Test fun lateAlarmIsCappedToOriginalDeadline() {
        val s = session()
        val late = clock(2000)
        assertEquals(TimerMath.Transition.COMPLETE, TimerMath.transition(s, late, zone))
        assertEquals(start.plusSeconds(1500), TimerMath.timing(s, late).endsAt)
        assertEquals(0L, TimerMath.timing(s, late).remainingSeconds)
    }

    @Test fun midnightDiscardsRunningAndPausedSessionsThatDidNotFinish() {
        val nearMidnight = session().copy(start = day.atTime(23, 50).atZone(zone).toInstant())
        val c = TimerMath.ClockReading(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), 610_000, 1)
        assertEquals(TimerMath.Transition.DISCARD, TimerMath.transition(nearMidnight, c, zone))
        assertEquals(TimerMath.Transition.DISCARD, TimerMath.transition(nearMidnight.copy(state = SessionState.PAUSED), c, zone))
    }

    @Test fun lateNextDayCallbackCreditsOnlyACompletionBeforeMidnight() {
        val endOfDay = day.plusDays(1).atStartOfDay(zone).toInstant()
        val s = session(60).copy(start = endOfDay.minusSeconds(120))
        val c = TimerMath.ClockReading(endOfDay.plusSeconds(600).toEpochMilli(), 730_000, 1)
        assertEquals(TimerMath.Transition.COMPLETE, TimerMath.transition(s, c, zone))
        assertEquals(endOfDay.minusSeconds(60), TimerMath.timing(s, c).endsAt)
        assertEquals(day.toEpochDay(), s.epochDay)
        assertEquals(TimerMath.Transition.DISCARD, TimerMath.transition(s.copy(limitSeconds = 180, remainingMs = 180_000), c, zone))
    }

    @Test fun exactMidnightFinishBelongsToOriginalDay() {
        val midnight = day.plusDays(1).atStartOfDay(zone).toInstant()
        val s = session(60).copy(start = midnight.minusSeconds(60))
        val c = TimerMath.ClockReading(midnight.toEpochMilli(), 70_000, 1)
        assertEquals(TimerMath.Transition.COMPLETE, TimerMath.transition(s, c, zone))
        assertEquals(day.toEpochDay(), s.epochDay)
    }

    @Test fun rebootDiscardsBothRunningAndPausedWithoutRecoveryOrCredit() {
        for (state in listOf(SessionState.RUNNING, SessionState.PAUSED)) {
            assertEquals(TimerMath.Transition.DISCARD, TimerMath.transition(session().copy(state = state), clock(5000, boot = 2), zone))
        }
        assertEquals(TimerMath.Transition.KEEP, TimerMath.transition(session().copy(state = SessionState.CLOSED), clock(5000, boot = 2), zone))
    }

    @Test fun staleOrDuplicateCallbacksCannotActOnResumedOrCompletedSessions() {
        val s = session()
        assertTrue(TimerMath.matchesCallback(s, s.id, s.generation))
        assertFalse(TimerMath.matchesCallback(s, s.id + 1, s.generation))
        val paused = TimerMath.pause(s, clock(100))
        assertFalse(TimerMath.matchesCallback(paused, s.id, s.generation))
        val resumed = TimerMath.resume(paused, clock(200))
        assertFalse(TimerMath.matchesCallback(resumed, s.id, s.generation))
        assertTrue(TimerMath.matchesCallback(resumed, resumed.id, resumed.generation))
        val completed = resumed.copy(state = SessionState.CLOSED, remainingMs = 0)
        assertFalse(TimerMath.matchesCallback(completed, resumed.id, resumed.generation))
        assertEquals(TimerMath.Transition.KEEP, TimerMath.transition(completed, clock(9000), zone))
    }

    @Test fun switchingTimersPausesPreviousAndResumesOnlyTheChosenLogicalSession() {
        val a = session()
        val firstSwitch = TimerMath.startSession(2, 600, listOf(a), clock(300), zone)
        val pausedA = firstSwitch.updates.single()
        val b = firstSwitch.newSession!!.copy(id = 8)
        assertEquals(SessionState.PAUSED, pausedA.state)
        assertEquals(1_200_000L, pausedA.remainingMs)
        assertEquals(600L, b.limitSeconds)
        val switchBack = TimerMath.startSession(1, 60, listOf(pausedA, b), clock(400), zone)
        assertNull(switchBack.newSession)
        val resumedA = switchBack.updates.single { it.habitId == 1L }
        val pausedB = switchBack.updates.single { it.habitId == 2L }
        assertEquals(a.id, resumedA.id)
        assertEquals(1500L, resumedA.limitSeconds) // Changed configuration affects new sessions only.
        assertEquals(1_200_000L, resumedA.remainingMs)
        assertEquals(SessionState.RUNNING, resumedA.state)
        assertEquals(SessionState.PAUSED, pausedB.state)
        assertEquals(500_000L, pausedB.remainingMs)
        assertEquals(1, switchBack.updates.count { it.state == SessionState.RUNNING })
        assertEquals(TimerMath.StartPlan(emptyList()),
            TimerMath.startSession(1, 300, switchBack.updates, clock(500), zone))
    }

    @Test fun freshSessionAlwaysUsesFullConfiguredDurationNotHistoryRemainder() {
        val plan = TimerMath.startSession(1, 1500, emptyList(), clock(), zone)
        assertEquals(1_500_000L, plan.newSession!!.remainingMs)
        assertEquals(1500L, plan.newSession.limitSeconds)
        assertEquals(day.toEpochDay(), plan.newSession.epochDay)
    }

    @Test fun countdownRoundsUpAndDoesNotFinishInLastFractionalSecond() {
        val c = clock(1499).copy(elapsedMs = clock(1499).elapsedMs + 999)
        assertEquals(1L, TimerMath.timing(session(), c).remainingSeconds)
        assertEquals(TimerMath.Transition.KEEP, TimerMath.transition(session(), c, zone))
        assertEquals(TimerMath.Transition.COMPLETE, TimerMath.transition(session(), clock(1500), zone))
    }

    @Test fun formattingAndCompletedCount() {
        assertEquals("1:05:00", TimerMath.formatClock(3900))
        assertEquals("1H40", TimerMath.formatGlyphCountdown(6000))
        assertEquals("1h 5m", TimerMath.formatDuration(3900))
        assertEquals(2, TimerMath.sessionsDone(3100, 1500, 4))
        assertEquals(4, TimerMath.sessionsDone(10000, 1500, 4))
    }
}
