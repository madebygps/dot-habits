package com.madebygps.dothabits.glyph

import com.madebygps.dothabits.domain.Habit
import com.madebygps.dothabits.domain.HabitHistory
import com.madebygps.dothabits.domain.HabitType
import com.madebygps.dothabits.domain.SessionState
import com.madebygps.dothabits.domain.SnapshotBuilder
import com.madebygps.dothabits.domain.TimerSession
import com.madebygps.dothabits.domain.TodaySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GlyphFramesTest {
    private val today = LocalDate.of(2026, 6, 3)
    private val now = today.atTime(18, 0).toInstant(ZoneOffset.UTC)
    private val deepWork = Habit(1, "Deep Work", "dot", HabitType.TIMED, 25, sessions = 4, createdOn = today, position = 1)
    private val read = Habit(2, "Read", "book", HabitType.TIMED, 60, createdOn = today, position = 2)

    private fun snap(vararg h: Pair<Habit, Long>, running: Habit? = null, runMinutes: Long = 5, pick: Long? = null): TodaySnapshot {
        val histories = h.map { (habit, secs) ->
            val sessions = if (habit == running) {
                val start = now.minusSeconds(runMinutes * 60)
                listOf(TimerSession(9, habit.id, start, null, SessionState.RUNNING, start, 1, limitSeconds = habit.sessionSeconds))
            } else emptyList()
            HabitHistory(habit, mapOf(today to secs + if (habit == running) runMinutes * 60 else 0), sessions)
        }
        return SnapshotBuilder.build(histories, today, DayOfWeek.MONDAY, now, pick)
    }

    private fun ringPixels(px: IntArray) = (0 until 625).filter { i ->
        val x = i % 25; val y = i / 25
        val d = sqrt((x - 12.0) * (x - 12.0) + (y - 12.0) * (y - 12.0))
        d in 10.5..12.5
    }

    /** Lit fraction of the ring, ignoring the dark gaps between session segments. */
    private fun litFraction(px: IntArray): Float {
        val seg = ringPixels(px).filter { px[it] != 0 }
        return seg.count { px[it] == 255 }.toFloat() / seg.size
    }

    private fun segmentCount(px: IntArray): Int {
        val steps = 720
        var runs = 0
        var prev = false
        var first = false
        for (k in 0 until steps) {
            val a = 2 * PI * k / steps
            val x = Math.round(12 + 11.5 * sin(a)).toInt()
            val y = Math.round(12 - 11.5 * cos(a)).toInt()
            val on = px[y * 25 + x] != 0
            if (k == 0) first = on
            if (on && !prev) runs++
            prev = on
        }
        if (first && prev) runs--
        return runs
    }

    @Test fun frameIs25x25AndMonochromeLevels() {
        val px = GlyphFrames.render(snap(deepWork to 0L))
        assertEquals(625, px.size)
        assertTrue(px.all { it in 0..255 })
    }

    @Test fun ringHasOneSegmentPerSession() {
        assertEquals(4, segmentCount(GlyphFrames.render(snap(deepWork to 0L))))
        // Single-session habit: one continuous ring.
        assertTrue(ringPixels(GlyphFrames.render(snap(read to 0L))).none { GlyphFrames.render(snap(read to 0L))[it] == 0 })
    }

    @Test fun ringFillReflectsSessionsDone() {
        assertEquals(0.5f, litFraction(GlyphFrames.render(snap(deepWork to 50 * 60L))), 0.06f)
        assertEquals(0.25f, litFraction(GlyphFrames.render(snap(deepWork to 25 * 60L))), 0.06f)
    }

    @Test fun runningAndPausedDiffer() {
        val paused = GlyphFrames.render(snap(deepWork to 5 * 60L))
        val running = GlyphFrames.render(snap(deepWork to 0L, running = deepWork, runMinutes = 5))
        assertFalse(paused.contentEquals(running))
        // Paused countdown is dimmed; running is full brightness.
        assertTrue(paused.any { it == 140 })
        assertFalse(running.any { it == 140 })
    }

    @Test fun countdownChangesAsTimeRuns() {
        val a = GlyphFrames.render(snap(deepWork to 0L, running = deepWork, runMinutes = 5))
        val b = GlyphFrames.render(snap(deepWork to 0L, running = deepWork, runMinutes = 6))
        assertFalse(a.contentEquals(b))
    }

    @Test fun allTimedDoneShowsCheckAndFullRing() {
        val s = snap(deepWork to 100 * 60L, read to 60 * 60L)
        assertEquals(null, s.activeTimer)
        val px = GlyphFrames.render(s)
        assertEquals(1f, litFraction(px), 0.001f)
    }

    @Test fun noTimedHabitsIsDimPlaceholder() {
        val px = GlyphFrames.render(TodaySnapshot(today, emptyList(), null))
        assertTrue(px.none { it == 255 })
    }

    @Test fun celebrationStaysOnRealLedsAndEndsOnCheck() {
        val frames = GlyphFrames.celebration()
        assertTrue(frames.size > 3)
        frames.dropLast(1).forEach { f -> (0 until 625).forEach { i -> if (f[i] != 0) assertTrue(GlyphFrames.isLed(i % 25, i / 25)) } }
        assertEquals(1f, litFraction(frames.last()), 0.001f)
    }

    @Test fun ledMaskMatchesPhone3Layout() {
        val rowCounts = (0 until 25).map { y -> (0 until 25).count { x -> GlyphFrames.isLed(x, y) } }
        assertEquals(listOf(7, 11, 15, 17, 19, 21, 21, 23, 23, 25, 25, 25, 25, 25, 25, 25, 23, 23, 21, 21, 19, 17, 15, 11, 7), rowCounts)
    }

    @Test fun holdPicksNextOpenTimerAndWraps() {
        val s = snap(deepWork to 0L, read to 0L)
        assertEquals(deepWork.id, s.activeTimer!!.habitId)
        assertEquals(read.id, s.activeTimer!!.next)
        val picked = snap(deepWork to 0L, read to 0L, pick = read.id).activeTimer!!
        assertEquals(read.id, picked.habitId)
        assertEquals(deepWork.id, picked.next)
    }

    @Test fun noSwitchingWhileRunningOrWithOneTimer() {
        assertEquals(null, snap(deepWork to 0L, read to 0L, running = deepWork, pick = read.id).activeTimer!!.let { assertEquals(deepWork.id, it.habitId); it.next })
        assertEquals(null, snap(deepWork to 0L).activeTimer!!.next)
    }

    @Test fun pickFallsBackWhenPickedHabitIsDone() {
        assertEquals(deepWork.id, snap(deepWork to 0L, read to 3600L, pick = read.id).activeTimer!!.habitId)
    }

    @Test fun choiceDotsOnlyWithSeveralTimers() {
        val one = GlyphFrames.render(snap(deepWork to 0L))
        val two = GlyphFrames.render(snap(deepWork to 0L, read to 0L))
        assertTrue((7..17).none { one[21 * 25 + it] != 0 })
        assertEquals(2, (7..17).count { two[21 * 25 + it] != 0 })
        assertEquals(255, two[21 * 25 + 11])
        assertFalse(GlyphFrames.picked(snap(deepWork to 0L, read to 0L)).contentEquals(two))
    }
}
