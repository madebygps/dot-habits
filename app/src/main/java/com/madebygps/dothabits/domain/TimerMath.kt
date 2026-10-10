package com.madebygps.dothabits.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Session timing only. Daily habit credit is written once, transactionally on completion. */
object TimerMath {
    data class ClockReading(val wallMs: Long, val elapsedMs: Long, val bootCount: Int)
    data class Timing(val remainingMs: Long, val endsAt: Instant?) {
        val remainingSeconds: Long get() = (remainingMs + 999) / 1000
    }

    enum class Transition { KEEP, COMPLETE, DISCARD }
    data class SessionChange(val session: TimerSession?, val credit: Entry? = null)
    data class StartPlan(val updates: List<TimerSession>, val newSession: TimerSession? = null)

    fun timing(session: TimerSession, clock: ClockReading): Timing {
        if (session.state == SessionState.CLOSED) return Timing(0, null)
        if (session.state == SessionState.PAUSED) return Timing(session.remainingMs, null)
        if (session.bootCount != clock.bootCount) return Timing(0, null)
        val elapsed = session.startElapsedMs?.let { (clock.elapsedMs - it).coerceAtLeast(0) }
            ?: (clock.wallMs - session.start.toEpochMilli()).coerceAtLeast(0)
        val remaining = (session.remainingMs - elapsed).coerceAtLeast(0)
        // Keep the actual deadline even when an alarm arrives late.
        val deadline = clock.wallMs + session.remainingMs - elapsed
        return Timing(remaining, Instant.ofEpochMilli(deadline))
    }

    /** Wall-projected sessions let all snapshot renderers use the same timing function. */
    fun timing(session: TimerSession, now: Instant): Timing =
        timing(session.copy(startElapsedMs = null), ClockReading(now.toEpochMilli(), 0, session.bootCount))

    fun transition(session: TimerSession, clock: ClockReading, zone: ZoneId): Transition {
        if (session.state == SessionState.CLOSED) return Transition.KEEP
        if (session.bootCount != clock.bootCount) return Transition.DISCARD
        val day = LocalDate.ofEpochDay(session.epochDay)
        val midnight = day.plusDays(1).atStartOfDay(zone).toInstant()
        val timing = timing(session, clock)
        // A delayed callback still credits a session that actually finished before midnight.
        if (session.state == SessionState.RUNNING && timing.remainingMs == 0L &&
            timing.endsAt != null && timing.endsAt <= midnight) return Transition.COMPLETE
        if (Instant.ofEpochMilli(clock.wallMs).atZone(zone).toLocalDate() != day) return Transition.DISCARD
        return Transition.KEEP
    }

    fun matchesCallback(session: TimerSession, id: Long, generation: Long): Boolean =
        session.id == id && session.generation == generation && session.state == SessionState.RUNNING

    fun settle(session: TimerSession, clock: ClockReading, zone: ZoneId): SessionChange =
        when (transition(session, clock, zone)) {
            Transition.KEEP -> SessionChange(session)
            Transition.DISCARD -> SessionChange(null)
            Transition.COMPLETE -> {
                val end = timing(session, clock).endsAt!!
                SessionChange(
                    session.copy(state = SessionState.CLOSED, remainingMs = 0, end = end),
                    Entry(habitId = session.habitId, date = LocalDate.ofEpochDay(session.epochDay),
                        amount = session.limitSeconds, createdAt = end),
                )
            }
        }

    /** Call after settling expiry, under the same database transaction. */
    fun startSession(habitId: Long, configuredSeconds: Long, unfinished: List<TimerSession>, clock: ClockReading, zone: ZoneId): StartPlan {
        require(configuredSeconds > 0)
        if (unfinished.any { it.habitId == habitId && it.state == SessionState.RUNNING })
            return StartPlan(emptyList())
        val updates = unfinished.filter { it.state == SessionState.RUNNING }.map { pause(it, clock) }
        val paused = unfinished.firstOrNull { it.habitId == habitId && it.state == SessionState.PAUSED }
        return if (paused != null) StartPlan(updates + resume(paused, clock))
        else StartPlan(updates, TimerSession(
            habitId = habitId, start = Instant.ofEpochMilli(clock.wallMs), end = null,
            state = SessionState.RUNNING, bootCount = clock.bootCount, limitSeconds = configuredSeconds,
            epochDay = Instant.ofEpochMilli(clock.wallMs).atZone(zone).toLocalDate().toEpochDay(),
            startElapsedMs = clock.elapsedMs,
        ))
    }

    fun pause(session: TimerSession, clock: ClockReading): TimerSession =
        session.copy(state = SessionState.PAUSED, remainingMs = timing(session, clock).remainingMs,
            startElapsedMs = null, generation = session.generation + 1)

    fun resume(session: TimerSession, clock: ClockReading): TimerSession =
        session.copy(state = SessionState.RUNNING, start = Instant.ofEpochMilli(clock.wallMs),
            startElapsedMs = clock.elapsedMs, bootCount = clock.bootCount, generation = session.generation + 1)

    fun project(session: TimerSession, clock: ClockReading): TimerSession =
        if (session.state != SessionState.RUNNING || session.bootCount != clock.bootCount) session
        else session.copy(start = timing(session, clock).endsAt!!.minusMillis(session.remainingMs), startElapsedMs = null)

    fun sessionsDone(todaySeconds: Long, sessionSeconds: Long, sessions: Int): Int =
        if (sessionSeconds <= 0) 0 else (todaySeconds.coerceAtLeast(0) / sessionSeconds).coerceAtMost(sessions.toLong()).toInt()

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun formatClock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }

    fun formatGlyphCountdown(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        if (s < 100 * 60) return "%d:%02d".format(s / 60, s % 60)
        val minutes = (s + 59) / 60
        return "%dH%02d".format(minutes / 60, minutes % 60)
    }
}
