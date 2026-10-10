package com.madebygps.dothabits.ui

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF

/** One clockwise, top-centred contour shared by the Compose and bitmap renderers. */
object TileGeometry {
    const val CORNER_FRACTION = 0.21f
    const val STROKE_FRACTION = 0.0625f
    const val FILL_ALPHA = 0.48f
    const val ICON_FRACTION = 0.40f
    const val DOT_RADIUS_FRACTION = 0.40f

    data class Segment(val start: Float, val end: Float)

    fun segments(count: Int): List<Segment> {
        if (count < 2) return listOf(Segment(0f, 1f))
        val span = 1f / count
        val gap = minOf(0.03f, span * 0.25f)
        return List(count) { i -> Segment(i * span + gap / 2, (i + 1) * span - gap / 2) }
    }

    fun filledSegments(fraction: Float, count: Int): List<Segment> {
        val f = fraction.coerceIn(0f, 1f)
        return if (count < 2) {
            if (f > 0) listOf(Segment(0f, f)) else emptyList()
        } else {
            segments(count).take((f * count + 0.0001f).toInt().coerceAtMost(count))
        }
    }

    fun contour(bounds: RectF): Path {
        val r = bounds.width() * CORNER_FRACTION
        return Path().apply {
            moveTo(bounds.centerX(), bounds.top)
            lineTo(bounds.right - r, bounds.top)
            arcTo(RectF(bounds.right - 2 * r, bounds.top, bounds.right, bounds.top + 2 * r), -90f, 90f)
            lineTo(bounds.right, bounds.bottom - r)
            arcTo(RectF(bounds.right - 2 * r, bounds.bottom - 2 * r, bounds.right, bounds.bottom), 0f, 90f)
            lineTo(bounds.left + r, bounds.bottom)
            arcTo(RectF(bounds.left, bounds.bottom - 2 * r, bounds.left + 2 * r, bounds.bottom), 90f, 90f)
            lineTo(bounds.left, bounds.top + r)
            arcTo(RectF(bounds.left, bounds.top, bounds.left + 2 * r, bounds.top + 2 * r), 180f, 90f)
            close()
        }
    }

    fun portion(measure: PathMeasure, start: Float, end: Float): Path = Path().apply {
        measure.getSegment(start * measure.length, end * measure.length, this, true)
    }
}
