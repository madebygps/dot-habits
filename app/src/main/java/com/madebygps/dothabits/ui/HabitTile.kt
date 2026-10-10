package com.madebygps.dothabits.ui

import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.madebygps.dothabits.domain.HabitTileState
import kotlin.math.roundToInt

@Composable
fun HabitTile(
    state: HabitTileState,
    modifier: Modifier = Modifier,
    holdProgress: Float = 0f,
    content: @Composable () -> Unit = {},
) {
    val colors = LocalDotColors.current
    val accent = if (state.dimmed) colors.dim else LocalHighlight.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val side = size.minDimension
            val stroke = side * TileGeometry.STROKE_FRACTION
            val inset = stroke / 2 + 1f
            val left = (size.width - side) / 2
            val top = (size.height - side) / 2
            val bounds = RectF(left + inset, top + inset, left + side - inset, top + side - inset)
            val contour = TileGeometry.contour(bounds)
            val measure = PathMeasure(contour, true)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                if (state.solid) {
                    paint.color = accent.toArgb()
                    paint.style = Paint.Style.FILL_AND_STROKE
                    paint.strokeWidth = stroke
                    native.drawPath(contour, paint)
                } else {
                    if (state.interiorFraction > 0) {
                        native.save()
                        native.clipPath(contour)
                        paint.color = accent.toArgb()
                        paint.alpha = (255 * TileGeometry.FILL_ALPHA).roundToInt()
                        native.drawRect(bounds.left, bounds.bottom - bounds.height() * state.interiorFraction, bounds.right, bounds.bottom, paint)
                        native.restore()
                    }
                    paint.alpha = 255
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = stroke
                    if (state.dashed) {
                        paint.color = accent.toArgb()
                        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(stroke * 0.6f, stroke * 0.6f), 0f)
                        native.drawPath(contour, paint)
                        paint.pathEffect = null
                    } else {
                        val segments = TileGeometry.segments(state.segments)
                        segments.forEach { segment ->
                            paint.color = colors.track.toArgb()
                            native.drawPath(TileGeometry.portion(measure, segment.start, segment.end), paint)
                        }
                        TileGeometry.filledSegments(state.borderFraction, state.segments).forEach { segment ->
                            paint.color = accent.toArgb()
                            native.drawPath(TileGeometry.portion(measure, segment.start, segment.end), paint)
                        }
                    }
                }
                if (holdProgress > 0) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = stroke * 0.3f
                    paint.color = (if (state.solid) colors.background else colors.text).toArgb()
                    native.drawPath(TileGeometry.portion(measure, 0f, holdProgress.coerceIn(0f, 1f)), paint)
                }
            }
        }
        content()
    }
}
