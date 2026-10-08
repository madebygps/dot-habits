package com.madebygps.dothabits.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madebygps.dothabits.domain.DotFont
import com.madebygps.dothabits.domain.DotIcons

object Palette {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0E0E0E)
    val Track = Color(0xFF262626)
    val Line = Color(0xFF1E1E1E)
    val Text = Color(0xFFF2F2F2)
    val Muted = Color(0xFF8A8A8A)
    val Dim = Color(0xFF5C5C5C)
}

val LocalHighlight = staticCompositionLocalOf { Color(0xFFE8343A) }

private val Mono = FontFamily.Monospace

@Composable
fun DotTheme(highlight: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = highlight,
        onPrimary = Color.Black,
        background = Palette.Black,
        onBackground = Palette.Text,
        surface = Palette.Surface,
        onSurface = Palette.Text,
        surfaceVariant = Palette.Surface,
        onSurfaceVariant = Palette.Muted,
        surfaceContainer = Palette.Surface,
        surfaceContainerHigh = Color(0xFF161616),
        surfaceContainerHighest = Color(0xFF1C1C1C),
        outline = Palette.Dim,
        secondary = Palette.Text,
    )
    val base = Typography()
    val typography = base.copy(
        labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp, color = Palette.Muted),
        labelMedium = TextStyle(fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.sp),
        labelLarge = TextStyle(fontFamily = Mono, fontSize = 14.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
    )
    CompositionLocalProvider(LocalHighlight provides highlight) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** Dot-matrix text accent using the original 3×5 [DotFont]. */
@Composable
fun DotText(text: String, modifier: Modifier = Modifier, dot: Dp = 3.dp, color: Color = Palette.Text) {
    val chars = text.uppercase()
    val cols = DotFont.width(chars).coerceAtLeast(1)
    Canvas(modifier.size(width = dot * cols, height = dot * DotFont.H)) {
        val cell = size.height / DotFont.H
        var x0 = 0
        for (c in chars) {
            val bits = DotFont.bits(c)
            for (i in bits.indices) if (bits[i]) {
                drawCircle(
                    color,
                    radius = cell * 0.42f,
                    center = Offset((x0 + i % DotFont.W + 0.5f) * cell, (i / DotFont.W + 0.5f) * cell),
                )
            }
            x0 += DotFont.W + 1
        }
    }
}

@Composable
fun DotIcon(name: String, modifier: Modifier = Modifier, color: Color = Palette.Text) {
    Canvas(modifier) {
        val bits = DotIcons.bits(name)
        val cell = size.minDimension / DotIcons.SIZE
        val ox = (size.width - cell * DotIcons.SIZE) / 2
        val oy = (size.height - cell * DotIcons.SIZE) / 2
        for (i in bits.indices) if (bits[i]) {
            drawCircle(
                color,
                radius = cell * RingGeometry.DOT_RADIUS_FRACTION,
                center = Offset(ox + (i % DotIcons.SIZE + 0.5f) * cell, oy + (i / DotIcons.SIZE + 0.5f) * cell),
            )
        }
    }
}

/** Progress ring (continuous or segmented) shared geometry with widgets. */
@Composable
fun Ring(
    fraction: Float,
    segments: Int,
    modifier: Modifier = Modifier,
    color: Color = LocalHighlight.current,
    dashedTrack: Boolean = false,
    holdProgress: Float = 0f,
    /** Goal met: draw a solid disc instead of an outline. */
    filled: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = size.minDimension * RingGeometry.STROKE_FRACTION
            val inset = stroke / 2f + 1f
            val arcSize = Size(size.minDimension - inset * 2, size.minDimension - inset * 2)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            val arcs = RingGeometry.arcs(fraction, segments)
            val trackStroke = Stroke(
                width = stroke,
                pathEffect = if (dashedTrack) PathEffect.dashPathEffect(floatArrayOf(stroke * 0.6f, stroke * 0.6f)) else null,
            )
            if (filled) {
                drawCircle(color, radius = arcSize.width / 2 + stroke / 2, center = center)
            } else {
                arcs.track.forEach { (s, sw) -> drawArc(Palette.Track, s, sw, false, topLeft, arcSize, style = trackStroke) }
                arcs.filled.forEach { (s, sw) -> drawArc(color, s, sw, false, topLeft, arcSize, style = Stroke(stroke)) }
            }
            if (holdProgress > 0f) {
                // Inner hold-to-complete indicator.
                val r = arcSize.width / 2 - stroke * 1.6f
                val holdColor = if (filled) Color.Black else color
                drawCircle(holdColor.copy(alpha = 0.18f * holdProgress), radius = r * holdProgress, center = center)
            }
        }
        content()
    }
}
