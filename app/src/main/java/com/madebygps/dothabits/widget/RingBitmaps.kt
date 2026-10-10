package com.madebygps.dothabits.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.madebygps.dothabits.domain.DotIcons
import com.madebygps.dothabits.domain.HabitToday
import com.madebygps.dothabits.domain.HabitTileState
import com.madebygps.dothabits.ui.TileGeometry
import kotlin.math.roundToInt

/** White bitmap masks of the app's shared rounded-square habit tile; Glance supplies the tints. */
object RingBitmaps {
    private const val MASK = 0xFFFFFFFF.toInt()

    enum class Tone { FOREGROUND, HIGHLIGHT, DIM, BACKGROUND }

    data class Layers(
        val fill: Bitmap,
        val track: Bitmap,
        val progress: Bitmap,
        val icon: Bitmap,
        val progressTone: Tone,
        val iconTone: Tone,
    )

    fun layers(t: HabitToday, sizePx: Int): Layers {
        val state = HabitTileState.from(t)
        val fill = createBitmap(sizePx, sizePx)
        val track = createBitmap(sizePx, sizePx)
        val progress = createBitmap(sizePx, sizePx)
        val icon = createBitmap(sizePx, sizePx)
        val stroke = sizePx * TileGeometry.STROKE_FRACTION
        val inset = stroke / 2f + 1f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val contour = TileGeometry.contour(rect)
        val measure = PathMeasure(contour, true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = stroke
            strokeCap = Paint.Cap.BUTT
            color = MASK
        }
        val progressCanvas = Canvas(progress)
        if (state.solid) {
            paint.style = Paint.Style.FILL_AND_STROKE
            progressCanvas.drawPath(contour, paint)
        } else {
            if (state.interiorFraction > 0f) {
                val fillCanvas = Canvas(fill)
                fillCanvas.save()
                fillCanvas.clipPath(contour)
                paint.alpha = (255 * TileGeometry.FILL_ALPHA).roundToInt()
                fillCanvas.drawRect(
                    rect.left, rect.bottom - rect.height() * state.interiorFraction,
                    rect.right, rect.bottom, paint,
                )
                fillCanvas.restore()
            }
            paint.alpha = 255
            paint.style = Paint.Style.STROKE
            if (state.dashed) {
                paint.pathEffect = DashPathEffect(floatArrayOf(stroke * 0.6f, stroke * 0.6f), 0f)
                progressCanvas.drawPath(contour, paint)
                paint.pathEffect = null
            } else {
                val segments = TileGeometry.segments(state.segments)
                val trackCanvas = Canvas(track)
                segments.forEach { segment ->
                    trackCanvas.drawPath(TileGeometry.portion(measure, segment.start, segment.end), paint)
                }
                TileGeometry.filledSegments(state.borderFraction, state.segments).forEach { segment ->
                    progressCanvas.drawPath(TileGeometry.portion(measure, segment.start, segment.end), paint)
                }
            }
        }
        drawIcon(Canvas(icon), sizePx, t.habit.icon)
        return Layers(
            fill = fill,
            track = track,
            progress = progress,
            icon = icon,
            progressTone = if (state.dimmed) Tone.DIM else Tone.HIGHLIGHT,
            iconTone = when {
                state.solid -> Tone.BACKGROUND
                state.dimmed -> Tone.DIM
                else -> Tone.FOREGROUND
            },
        )
    }

    private fun drawIcon(c: Canvas, sizePx: Int, icon: String) {
        val bits = DotIcons.bits(icon)
        val area = sizePx * TileGeometry.ICON_FRACTION
        val cell = area / DotIcons.SIZE
        val origin = (sizePx - area) / 2f
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MASK }
        for (i in bits.indices) if (bits[i]) {
            val x = origin + (i % DotIcons.SIZE + 0.5f) * cell
            val y = origin + (i / DotIcons.SIZE + 0.5f) * cell
            c.drawCircle(x, y, cell * TileGeometry.DOT_RADIUS_FRACTION, dot)
        }
    }
}
