package com.madebygps.dothabits.glyph

import com.madebygps.dothabits.domain.DotFont
import com.madebygps.dothabits.domain.DotIcons
import com.madebygps.dothabits.domain.TimerMath
import com.madebygps.dothabits.domain.TodaySnapshot
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/** The two Glyph Toy views. A Glyph Button long-press toggles between them; nothing else. */
enum class GlyphView { TODAY, TIMER }

/**
 * Pure renderer producing 25×25 monochrome grey levels (0..255, row-major) for the
 * Phone (3) Glyph Matrix. Kept free of Android/SDK types so it is unit-tested on the JVM.
 * The Glyph is always monochrome regardless of the app highlight colour.
 */
object GlyphFrames {
    const val SIZE = 25
    private const val CENTER = 12.0
    private const val ON = 255
    private const val TRACK = 40

    fun render(view: GlyphView, snapshot: TodaySnapshot): IntArray = when (view) {
        GlyphView.TODAY -> today(snapshot)
        GlyphView.TIMER -> timer(snapshot)
    }

    fun today(snapshot: TodaySnapshot): IntArray {
        val px = IntArray(SIZE * SIZE)
        ring(px, snapshot.overallFraction, track = snapshot.dueCount > 0)
        when {
            snapshot.dueCount == 0 -> text(px, "--", 10)
            snapshot.doneCount == snapshot.dueCount -> icon(px, "check")
            else -> text(px, "${snapshot.doneCount}/${snapshot.dueCount}", 10)
        }
        return px
    }

    fun timer(snapshot: TodaySnapshot): IntArray {
        val px = IntArray(SIZE * SIZE)
        val t = snapshot.activeTimer
        if (t == null) {
            pauseBars(px, TRACK * 2)
            text(px, "--", 13, TRACK * 2)
            return px
        }
        ring(px, if (t.goalSeconds > 0) (t.todaySeconds.toFloat() / t.goalSeconds).coerceIn(0f, 1f) else 0f, track = true)
        if (t.running) playTriangle(px) else pauseBars(px, ON)
        text(px, TimerMath.formatGlyphClock(t.todaySeconds), 13)
        return px
    }

    private fun ring(px: IntArray, fraction: Float, track: Boolean) {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val dx = x - CENTER
            val dy = y - CENTER
            val d = sqrt(dx * dx + dy * dy)
            if (d < 10.5 || d > 12.5) continue
            // Angle measured clockwise from 12 o'clock, 0..1.
            var a = atan2(dx, -dy) / (2 * PI)
            if (a < 0) a += 1.0
            px[y * SIZE + x] = if (a < fraction) ON else if (track) TRACK else 0
        }
    }

    private fun text(px: IntArray, s: String, top: Int, level: Int = ON) {
        val w = DotFont.width(s)
        var x0 = (SIZE - w) / 2
        for (c in s) {
            val bits = DotFont.bits(c)
            for (i in bits.indices) if (bits[i]) set(px, x0 + i % DotFont.W, top + i / DotFont.W, level)
            x0 += DotFont.W + 1
        }
    }

    private fun icon(px: IntArray, name: String) {
        val bits = DotIcons.bits(name)
        val off = (SIZE - DotIcons.SIZE) / 2
        for (i in bits.indices) if (bits[i]) set(px, off + i % DotIcons.SIZE, off + i / DotIcons.SIZE, ON)
    }

    private fun playTriangle(px: IntArray) {
        // 4 wide × 7 tall right-pointing triangle above the digits.
        for (row in 0 until 7) {
            val len = if (row < 4) row + 1 else 7 - row
            for (c in 0 until len) set(px, 11 + c, 4 + row, ON)
        }
    }

    private fun pauseBars(px: IntArray, level: Int) {
        for (row in 0 until 6) {
            set(px, 10, 5 + row, level); set(px, 11, 5 + row, level)
            set(px, 13, 5 + row, level); set(px, 14, 5 + row, level)
        }
    }

    private fun set(px: IntArray, x: Int, y: Int, v: Int) {
        if (x in 0 until SIZE && y in 0 until SIZE) px[y * SIZE + x] = v
    }
}
