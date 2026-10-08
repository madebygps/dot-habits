package com.madebygps.dothabits.glyph

import com.madebygps.dothabits.domain.DotFont
import com.madebygps.dothabits.domain.DotIcons
import com.madebygps.dothabits.domain.HabitType
import com.madebygps.dothabits.domain.TimerMath
import com.madebygps.dothabits.domain.TodaySnapshot
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure renderer producing 25×25 monochrome grey levels (0..255, row-major) for the
 * Phone (3) Glyph Matrix. Kept free of Android/SDK types so it is unit-tested on the JVM.
 * The Glyph is always monochrome regardless of the app highlight colour.
 *
 * The toy is a timer: it shows [TodaySnapshot.activeTimer] (the running timer, otherwise the
 * first timed habit due today that isn't done), one ring segment per session, and an m:ss
 * countdown of the current session in the middle.
 */
object GlyphFrames {
    const val SIZE = 25
    private const val CENTER = 12.0
    private const val ON = 255
    private const val TRACK = 40
    private const val PAUSED_TEXT = 140

    /** Matches the LED layout in Nothing's Phone (3) Glyph preview specification. */
    private const val LED_RADIUS_SQ = 12.5 * 12.5

    fun render(snapshot: TodaySnapshot): IntArray {
        val px = IntArray(SIZE * SIZE)
        val t = snapshot.activeTimer
        if (t == null) {
            val anyTimedToday = snapshot.habits.any { it.habit.type == HabitType.TIMED && it.countsTowardToday }
            if (anyTimedToday) {
                // Every timed habit is done today.
                ring(px, 1f)
                icon(px, "check")
            } else {
                ring(px, 0f)
                text(px, "--", 10, TRACK * 2)
            }
            return px
        }
        val sessionSecs = t.sessionSeconds.coerceAtLeast(1)
        val n = t.sessions.coerceAtLeast(1)
        segmentedRing(px, List(n) { i -> (t.todaySeconds.toFloat() / sessionSecs - i).coerceIn(0f, 1f) })
        if (t.running) playMark(px) else pauseMark(px)
        val label = TimerMath.formatGlyphCountdown(t.sessionRemaining)
        val level = if (t.running) ON else PAUSED_TEXT
        text(px, label, 11, level)
        choiceDots(px, t.choices.size, t.choices.indexOf(t.habitId))
        return px
    }

    /**
     * Shown briefly after the hold gesture switches timers: the habit's icon (so you can tell
     * which one is selected), its ring and the choice dots.
     */
    fun picked(snapshot: TodaySnapshot): IntArray {
        val t = snapshot.activeTimer ?: return render(snapshot)
        val px = IntArray(SIZE * SIZE)
        val sessionSecs = t.sessionSeconds.coerceAtLeast(1)
        segmentedRing(px, List(t.sessions.coerceAtLeast(1)) { i -> (t.todaySeconds.toFloat() / sessionSecs - i).coerceIn(0f, 1f) })
        icon(px, t.icon)
        choiceDots(px, t.choices.size, t.choices.indexOf(t.habitId))
        return px
    }

    /** One dot per pickable timer on row 21, the selected one lit; nothing when there's only one. */
    private fun choiceDots(px: IntArray, n: Int, selected: Int) {
        if (n < 2) return
        val shown = minOf(n, 6)
        val x0 = CENTER.toInt() - (shown - 1)
        for (i in 0 until shown) set(px, x0 + i * 2, 21, if (i == selected.coerceIn(0, shown - 1)) ON else TRACK * 2)
    }

    /**
     * Short "session done" animation: a pulse expanding from the centre, then the check.
     * Played by the toy service when a session finishes while the toy is on screen.
     */
    fun celebration(): List<IntArray> {
        val frames = ArrayList<IntArray>()
        repeat(2) {
            for (r in listOf(1.5, 4.0, 6.5, 9.0, 11.5)) {
                val px = IntArray(SIZE * SIZE)
                for (y in 0 until SIZE) for (x in 0 until SIZE) {
                    val d = sqrt((x - CENTER) * (x - CENTER) + (y - CENTER) * (y - CENTER))
                    if (d in (r - 1.2)..(r + 1.2) && isLed(x, y)) px[y * SIZE + x] = ON
                    else if (d in (r - 3.0)..(r - 1.2) && isLed(x, y)) px[y * SIZE + x] = TRACK * 2
                }
                frames += px
            }
        }
        frames += IntArray(SIZE * SIZE).also { ring(it, 1f); icon(it, "check") }
        return frames
    }

    /** True for the LEDs physically present on the Phone (3) circular 25×25 matrix. */
    fun isLed(x: Int, y: Int): Boolean {
        val dx = x - CENTER
        val dy = y - CENTER
        return dx * dx + dy * dy <= LED_RADIUS_SQ
    }

    private inline fun forRing(action: (i: Int, angle: Double) -> Unit) {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val dx = x - CENTER
            val dy = y - CENTER
            val d = sqrt(dx * dx + dy * dy)
            if (d < 10.5 || d > 12.5) continue
            // Angle measured clockwise from 12 o'clock, 0..1.
            var a = atan2(dx, -dy) / (2 * PI)
            if (a < 0) a += 1.0
            action(y * SIZE + x, a)
        }
    }

    private fun ring(px: IntArray, fraction: Float) = forRing { i, a ->
        px[i] = if (a < fraction) ON else TRACK
    }

    private fun segmentedRing(px: IntArray, fractions: List<Float>) {
        val n = fractions.size
        val gap = if (n > 1) minOf(0.03, 0.35 / n) * n else 0.0
        forRing { i, a ->
            val seg = minOf((a * n).toInt(), n - 1)
            val local = a * n - seg
            px[i] = when {
                local < gap / 2 || local > 1 - gap / 2 -> 0
                local - gap / 2 < fractions[seg] * (1 - gap) -> ON
                else -> TRACK
            }
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

    /** 3 × 5 right-pointing triangle above the countdown. */
    private fun playMark(px: IntArray) {
        for (row in 0 until 5) {
            val len = if (row < 3) row + 1 else 5 - row
            for (c in 0 until len) set(px, 11 + c, 3 + row, ON)
        }
    }

    /** Two 1 × 5 bars above the countdown. */
    private fun pauseMark(px: IntArray) {
        for (row in 0 until 5) {
            set(px, 10, 3 + row, ON)
            set(px, 14, 3 + row, ON)
        }
    }

    private fun set(px: IntArray, x: Int, y: Int, v: Int) {
        if (x in 0 until SIZE && y in 0 until SIZE) px[y * SIZE + x] = v
    }
}
