package com.madebygps.dothabits.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Timer accounting rules (documented in README "Timers"):
 *  - A running session counts up to `now`.
 *  - A NEEDS_REVIEW session (interrupted by a reboot) counts only up to its last
 *    confirmed-alive moment, so a reboot can never silently over-count.
 *  - Time is credited to the calendar day it actually happened on; a session that
 *    crosses midnight is split between both days.
 *  - Running time is measured on the monotonic clock (elapsedRealtime) within one boot, so
 *    manual or network wall-clock changes don't add or remove time; the wall clock only
 *    decides which calendar day the time belongs to.
 */
object TimerMath {

    data class ClockReading(val wallMs: Long, val elapsedMs: Long, val bootCount: Int)

    /**
     * Start time expressed in the *current* wall-clock frame: now minus monotonic elapsed time.
     * Falls back to the stored wall start when no monotonic stamp exists (sessions from schema v1)
     * or it's implausible (elapsed clock went backwards).
     */
    fun rebasedStartMs(startWallMs: Long, startElapsedMs: Long?, now: ClockReading): Long {
        if (startElapsedMs == null) return startWallMs
        val elapsed = now.elapsedMs - startElapsedMs
        return if (elapsed < 0) startWallMs else now.wallMs - elapsed
    }

    fun effectiveEnd(session: TimerSession, now: Instant): Instant = when (session.state) {
        SessionState.RUNNING -> minOf(maxOf(now, session.start), limitEnd(session))
        SessionState.NEEDS_REVIEW -> minOf(maxOf(session.lastAlive, session.start), limitEnd(session))
        SessionState.CLOSED -> session.end ?: session.start
    }

    /** The moment a run reaches its session limit (far future when it has none). */
    fun limitEnd(session: TimerSession): Instant =
        session.limitSeconds?.let { session.start.plusSeconds(it) } ?: Instant.MAX

    /** A RUNNING run that has reached its limit is finished even before it's persisted as CLOSED. */
    fun isLive(session: TimerSession, now: Instant): Boolean =
        session.state == SessionState.RUNNING && now < limitEnd(session)

    /** Seconds left in the current session given today's total, e.g. 25-min sessions at 30 min → 20 min. */
    fun sessionRemaining(todaySeconds: Long, sessionSeconds: Long): Long {
        if (sessionSeconds <= 0) return 0
        return sessionSeconds - todaySeconds.coerceAtLeast(0) % sessionSeconds
    }

    /** Whole sessions completed today, capped at the daily number of sessions. */
    fun sessionsDone(todaySeconds: Long, sessionSeconds: Long, sessions: Int): Int =
        if (sessionSeconds <= 0) 0 else (todaySeconds / sessionSeconds).toInt().coerceAtMost(sessions)

    fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
        date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()

    fun secondsOnDay(sessions: List<TimerSession>, date: LocalDate, zone: ZoneId, now: Instant): Long {
        val (dayStart, dayEnd) = dayBounds(date, zone)
        return sessions.sumOf { millisecondsWithin(it, dayStart, dayEnd, now) } / 1000
    }

    private fun millisecondsWithin(session: TimerSession, dayStart: Instant, dayEnd: Instant, now: Instant): Long {
        val from = maxOf(session.start, dayStart)
        val to = minOf(effectiveEnd(session, now), dayEnd)
        return if (to > from) Duration.between(from, to).toMillis() else 0L
    }

    /** Seconds per day for every day touched by [sessions]. */
    fun secondsByDay(sessions: List<TimerSession>, zone: ZoneId, now: Instant): Map<LocalDate, Long> {
        val out = HashMap<LocalDate, Long>()
        for (s in sessions) {
            val end = effectiveEnd(s, now)
            if (end <= s.start) continue
            var day = s.start.atZone(zone).toLocalDate()
            val lastDay = end.atZone(zone).toLocalDate()
            while (!day.isAfter(lastDay)) {
                val (dayStart, dayEnd) = dayBounds(day, zone)
                val millis = millisecondsWithin(s, dayStart, dayEnd, now)
                if (millis > 0) out[day] = (out[day] ?: 0L) + millis
                day = day.plusDays(1)
            }
        }
        // Round once per day, not once per pause, so short runs still accumulate.
        return out.mapValues { (_, millis) -> millis / 1000 }
    }


    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    /** "12:05" minutes:seconds (or "1:05:00" with hours) countdown text. */
    fun formatClock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    /** Glyph countdown: m:ss up to 99:59 (fits the 25-LED width), then 1H40-style hours+minutes. */
    fun formatGlyphCountdown(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return if (s < 100 * 60) "%d:%02d".format(s / 60, s % 60)
        else {
            val minutes = (s + 59) / 60
            "%dH%02d".format(minutes / 60, minutes % 60)
        }
    }
}
