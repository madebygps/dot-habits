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
        SessionState.RUNNING -> maxOf(now, session.start)
        SessionState.NEEDS_REVIEW -> maxOf(session.lastAlive, session.start)
        SessionState.CLOSED -> session.end ?: session.start
    }

    fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
        date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()

    fun secondsOnDay(sessions: List<TimerSession>, date: LocalDate, zone: ZoneId, now: Instant): Long {
        val (dayStart, dayEnd) = dayBounds(date, zone)
        return sessions.sumOf { s ->
            val from = maxOf(s.start, dayStart)
            val to = minOf(effectiveEnd(s, now), dayEnd)
            if (to > from) Duration.between(from, to).seconds else 0L
        }
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
                val secs = secondsOnDay(listOf(s), day, zone, now)
                if (secs > 0) out[day] = (out[day] ?: 0L) + secs
                day = day.plusDays(1)
            }
        }
        return out
    }

    /** When a session started now would reach the remaining goal, or null if already reached. */
    fun goalReachedAt(alreadySeconds: Long, goalSeconds: Long, now: Instant): Instant? {
        val remaining = goalSeconds - alreadySeconds
        return if (remaining > 0) now.plusSeconds(remaining) else null
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

    /** Compact clock for the 25×25 Glyph matrix: "37" minutes or "1:05" hours:minutes. */
    fun formatGlyphClock(seconds: Long): String {
        val totalMin = seconds / 60
        return if (totalMin < 100) totalMin.toString() else "${totalMin / 60}:${(totalMin % 60).toString().padStart(2, '0')}"
    }
}
