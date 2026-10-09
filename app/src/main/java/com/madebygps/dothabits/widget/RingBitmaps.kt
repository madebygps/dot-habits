package com.madebygps.dothabits.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.madebygps.dothabits.domain.DotIcons
import com.madebygps.dothabits.domain.HabitToday
import com.madebygps.dothabits.domain.TodayStatus
import com.madebygps.dothabits.ui.RingGeometry

/** Canvas rendering of the same ring the app draws in Compose (shared [RingGeometry]). */
object RingBitmaps {
    private const val MASK = 0xFFFFFFFF.toInt()

    enum class Tone { FOREGROUND, DIM, BACKGROUND }

    data class Layers(
        val track: Bitmap,
        val progress: Bitmap,
        val icon: Bitmap,
        val progressTone: Tone,
        val iconTone: Tone,
    )

    fun layers(t: HabitToday, sizePx: Int): Layers {
        val track = createBitmap(sizePx, sizePx)
        val progress = createBitmap(sizePx, sizePx)
        val icon = createBitmap(sizePx, sizePx)
        val stroke = sizePx * RingGeometry.STROKE_FRACTION
        val inset = stroke / 2f + 1f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.BUTT
            color = MASK
        }
        val arcs = RingGeometry.arcs(t.fraction, t.segments)
        if (t.status == TodayStatus.DONE) {
            Canvas(progress).drawCircle(
                sizePx / 2f,
                sizePx / 2f,
                sizePx / 2f - 1f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MASK },
            )
        } else {
            val trackCanvas = Canvas(track)
            if (t.habit.isNegative) {
                paint.pathEffect = DashPathEffect(floatArrayOf(stroke * 0.6f, stroke * 0.6f), 0f)
            }
            arcs.track.forEach { (start, sweep) -> trackCanvas.drawArc(rect, start, sweep, false, paint) }
            paint.pathEffect = null
            val progressCanvas = Canvas(progress)
            arcs.filled.forEach { (start, sweep) -> progressCanvas.drawArc(rect, start, sweep, false, paint) }
        }
        drawIcon(Canvas(icon), sizePx, t.habit.icon)
        return Layers(
            track = track,
            progress = progress,
            icon = icon,
            progressTone = if (t.status == TodayStatus.SLIPPED) Tone.DIM else Tone.FOREGROUND,
            iconTone = when (t.status) {
                TodayStatus.DONE -> Tone.BACKGROUND
                TodayStatus.REST -> Tone.DIM
                else -> Tone.FOREGROUND
            },
        )
    }

    private fun drawIcon(c: Canvas, sizePx: Int, icon: String) {
        val bits = DotIcons.bits(icon)
        val area = sizePx * RingGeometry.ICON_FRACTION
        val cell = area / DotIcons.SIZE
        val origin = (sizePx - area) / 2f
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MASK }
        for (i in bits.indices) if (bits[i]) {
            val x = origin + (i % DotIcons.SIZE + 0.5f) * cell
            val y = origin + (i / DotIcons.SIZE + 0.5f) * cell
            c.drawCircle(x, y, cell * RingGeometry.DOT_RADIUS_FRACTION, dot)
        }
    }
}
