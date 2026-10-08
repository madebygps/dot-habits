package com.madebygps.dothabits.glyph

import com.madebygps.dothabits.domain.ActiveTimer
import com.madebygps.dothabits.domain.Habit
import com.madebygps.dothabits.domain.HabitHistory
import com.madebygps.dothabits.domain.HabitType
import com.madebygps.dothabits.domain.SnapshotBuilder
import com.madebygps.dothabits.domain.TodaySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

class GlyphFramesTest {
    private val today = LocalDate.of(2026, 6, 3)

    private fun snapshot(done: Int, total: Int): TodaySnapshot {
        val histories = (1..total).map { i ->
            HabitHistory(
                Habit(i.toLong(), "h$i", "dot", HabitType.COUNT, 1, createdOn = today, position = i),
                if (i <= done) mapOf(today to 1L) else emptyMap(),
            )
        }
        return SnapshotBuilder.build(histories, today, DayOfWeek.MONDAY, Instant.EPOCH)
    }

    /** Fraction of ring pixels lit at full brightness. */
    private fun litRingFraction(px: IntArray): Float {
        var ring = 0
        var lit = 0
        for (y in 0 until 25) for (x in 0 until 25) {
            val d = sqrt((x - 12.0) * (x - 12.0) + (y - 12.0) * (y - 12.0))
            if (d < 10.5 || d > 12.5) continue
            ring++
            if (px[y * 25 + x] == 255) lit++
        }
        return lit.toFloat() / ring
    }

    @Test fun frameIs25x25AndMonochromeLevels() {
        val px = GlyphFrames.today(snapshot(2, 6))
        assertEquals(625, px.size)
        assertTrue(px.all { it in 0..255 })
    }

    @Test fun todayRingMatchesSnapshotProgress() {
        for ((done, total) in listOf(0 to 6, 2 to 6, 3 to 6, 5 to 6)) {
            val snap = snapshot(done, total)
            assertEquals(snap.overallFraction, litRingFraction(GlyphFrames.today(snap)), 0.06f)
        }
    }

    @Test fun ringFillsClockwiseFromTop() {
        val px = GlyphFrames.today(snapshot(1, 4))
        // Pixel at 12 o'clock outer ring lit, pixel at 9 o'clock not.
        assertEquals(255, px[0 * 25 + 12])
        assertFalse(px[12 * 25 + 0] == 255)
        val angle = atan2(1.0, 0.0) / (2 * PI)
        assertTrue(angle in 0.0..1.0)
    }

    @Test fun allDoneShowsCheckNotCount() {
        val all = GlyphFrames.today(snapshot(3, 3))
        val partial = GlyphFrames.today(snapshot(2, 3))
        assertFalse(all.contentEquals(partial))
        assertEquals(1f, litRingFraction(all), 0.001f)
    }

    @Test fun timerViewWithoutTimerIsDimPlaceholder() {
        val px = GlyphFrames.timer(TodaySnapshot(today, emptyList(), null))
        assertTrue(px.none { it == 255 })
    }

    @Test fun timerViewReflectsTimerProgressAndState() {
        val running = TodaySnapshot(today, emptyList(), ActiveTimer(1, "Read", 30 * 60, 60 * 60, running = true))
        val paused = running.copy(activeTimer = running.activeTimer!!.copy(running = false))
        assertEquals(0.5f, litRingFraction(GlyphFrames.timer(running)), 0.06f)
        assertFalse(GlyphFrames.timer(running).contentEquals(GlyphFrames.timer(paused)))
    }

    @Test fun renderSelectsView() {
        val s = snapshot(1, 2)
        assertTrue(GlyphFrames.render(GlyphView.TODAY, s).contentEquals(GlyphFrames.today(s)))
        assertTrue(GlyphFrames.render(GlyphView.TIMER, s).contentEquals(GlyphFrames.timer(s)))
    }
}
