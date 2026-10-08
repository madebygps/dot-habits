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
    const val TRACK = 0xFF262626.toInt()
    const val ICON = 0xFFF2F2F2.toInt()
    const val DIM = 0xFF5C5C5C.toInt()
    const val BLACK = 0xFF000000.toInt()

    fun habit(t: HabitToday, sizePx: Int, highlight: Int): Bitmap =
        ring(
            sizePx = sizePx,
            fraction = t.fraction,
            segments = t.segments,
            highlight = if (t.status == TodayStatus.SLIPPED) DIM else highlight,
            dashedTrack = t.habit.isNegative,
            icon = t.habit.icon,
            iconColor = when (t.status) {
                TodayStatus.DONE -> BLACK
                TodayStatus.REST -> DIM
                else -> ICON
            },
            filled = t.status == TodayStatus.DONE,
        )

    fun ring(
        sizePx: Int,
        fraction: Float,
        segments: Int,
        highlight: Int,
        dashedTrack: Boolean = false,
        icon: String? = null,
        iconColor: Int = ICON,
        filled: Boolean = false,
    ): Bitmap {
        val bmp = createBitmap(sizePx, sizePx)
        val c = Canvas(bmp)
        val stroke = sizePx * RingGeometry.STROKE_FRACTION
        val inset = stroke / 2f + 1f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.BUTT
        }
        val arcs = RingGeometry.arcs(fraction, segments)
        if (filled) {
            c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = highlight })
        } else drawArcs(c, rect, paint, arcs, highlight, dashedTrack, stroke)
        drawIcon(c, sizePx, icon, iconColor)
        return bmp
    }

    private fun drawArcs(c: Canvas, rect: RectF, paint: Paint, arcs: RingGeometry.Arcs, highlight: Int, dashedTrack: Boolean, stroke: Float) {
        paint.color = TRACK
        if (dashedTrack) paint.pathEffect = DashPathEffect(floatArrayOf(stroke * 0.6f, stroke * 0.6f), 0f)
        arcs.track.forEach { (start, sweep) -> c.drawArc(rect, start, sweep, false, paint) }
        paint.pathEffect = null
        paint.color = highlight
        arcs.filled.forEach { (start, sweep) -> c.drawArc(rect, start, sweep, false, paint) }
    }

    private fun drawIcon(c: Canvas, sizePx: Int, icon: String?, iconColor: Int) {
        if (icon != null) {
            val bits = DotIcons.bits(icon)
            val area = sizePx * RingGeometry.ICON_FRACTION
            val cell = area / DotIcons.SIZE
            val origin = (sizePx - area) / 2f
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = iconColor }
            for (i in bits.indices) if (bits[i]) {
                val x = origin + (i % DotIcons.SIZE + 0.5f) * cell
                val y = origin + (i / DotIcons.SIZE + 0.5f) * cell
                c.drawCircle(x, y, cell * RingGeometry.DOT_RADIUS_FRACTION, dot)
            }
        }
    }
}
