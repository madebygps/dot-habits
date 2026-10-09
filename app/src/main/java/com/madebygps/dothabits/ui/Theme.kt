package com.madebygps.dothabits.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madebygps.dothabits.domain.DotFont
import com.madebygps.dothabits.domain.DotIcons
import java.util.Locale

data class DotColors(
    val background: Color,
    val surface: Color,
    val track: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
)

private val DarkDotColors = DotColors(
    background = Color(0xFF000000),
    surface = Color(0xFF0E0E0E),
    track = Color(0xFF262626),
    line = Color(0xFF1E1E1E),
    text = Color(0xFFF2F2F2),
    muted = Color(0xFF8A8A8A),
    dim = Color(0xFF5C5C5C),
)

private val LightDotColors = DotColors(
    background = Color(0xFFF4F4F2),
    surface = Color(0xFFFFFFFF),
    track = Color(0xFFD2D2CE),
    line = Color(0xFFDDDDD9),
    text = Color(0xFF111111),
    muted = Color(0xFF666663),
    dim = Color(0xFF94948F),
)

val LocalDotColors = staticCompositionLocalOf { DarkDotColors }

object Palette {
    val Black: Color
        @Composable @ReadOnlyComposable get() = LocalDotColors.current.background
    val Surface: Color
        @Composable @ReadOnlyComposable get() = LocalDotColors.current.surface
    val Track: Color
        @Composable @ReadOnlyComposable get() = LocalDotColors.current.track
    val Line: Color
        @Composable @ReadOnlyComposable get() = LocalDotColors.current.line
    val Text: Color
        @Composable @ReadOnlyComposable get() = LocalDotColors.current.text
    val Muted: Color
        @Composable @ReadOnlyComposable get() = LocalDotColors.current.muted
    val Dim: Color
        @Composable @ReadOnlyComposable get() = LocalDotColors.current.dim
}

val LocalHighlight = staticCompositionLocalOf { Color(0xFFE8343A) }

private val Mono = FontFamily.Monospace

@Composable
fun DotTheme(highlight: Color, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkDotColors else LightDotColors
    val displayHighlight = if (!dark && highlight.luminance() > 0.75f) Color(0xFF565652) else highlight
    val scheme = if (dark) {
        darkColorScheme(
            primary = displayHighlight,
            onPrimary = Color.Black,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.muted,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = Color(0xFF161616),
            surfaceContainerHighest = Color(0xFF1C1C1C),
            outline = colors.dim,
            secondary = colors.text,
        )
    } else {
        lightColorScheme(
            primary = displayHighlight,
            onPrimary = Color.Black,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.muted,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = Color(0xFFEAEAE7),
            surfaceContainerHighest = Color(0xFFE1E1DD),
            outline = colors.dim,
            secondary = colors.text,
        )
    }
    val base = Typography()
    val typography = base.copy(
        labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp, color = colors.muted),
        labelMedium = TextStyle(fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.sp),
        labelLarge = TextStyle(fontFamily = Mono, fontSize = 14.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
    )
    CompositionLocalProvider(LocalHighlight provides displayHighlight, LocalDotColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

@Composable
fun DotScreenTitle(text: String) {
    DotText(text, modifier = Modifier.semantics(mergeDescendants = true) { heading() }, dot = 4.dp)
}

internal fun fitsDotText(text: String, cell: Dp, maxWidth: Dp, maxHeight: Dp): Boolean =
    text.all(DotFont::supports) &&
        cell * DotFont.width(text).coerceAtLeast(1) <= maxWidth &&
        cell * DotFont.H <= maxHeight

/** Scalable dot accent with readable text when glyphs or available space cannot support it. */
@Composable
fun DotText(text: String, modifier: Modifier = Modifier, dot: Dp = 3.dp, color: Color? = null) {
    val resolvedColor = color ?: LocalDotColors.current.text
    val fontSize = (dot.value * DotFont.H).sp
    val scaledCell = with(LocalDensity.current) { fontSize.toDp() / DotFont.H }
    val chars = text.uppercase(Locale.ROOT)
    BoxWithConstraints(modifier) {
        if (!fitsDotText(text, scaledCell, maxWidth, maxHeight)) {
            Text(text, fontSize = fontSize, color = resolvedColor)
        } else {
            val cols = DotFont.width(chars).coerceAtLeast(1)
            Canvas(
                Modifier
                    .size(width = scaledCell * cols, height = scaledCell * DotFont.H)
                    .semantics { this.text = AnnotatedString(text) },
            ) {
                val cell = size.height / DotFont.H
                var x0 = 0
                for (c in chars) {
                    val bits = DotFont.bits(c)
                    for (i in bits.indices) if (bits[i]) {
                        drawCircle(
                            resolvedColor,
                            radius = cell * 0.42f,
                            center = Offset((x0 + i % DotFont.W + 0.5f) * cell, (i / DotFont.W + 0.5f) * cell),
                        )
                    }
                    x0 += DotFont.W + 1
                }
            }
        }
    }
}

@Composable
fun DotIcon(name: String, modifier: Modifier = Modifier, color: Color? = null) {
    val resolvedColor = color ?: LocalDotColors.current.text
    Canvas(modifier) {
        val bits = DotIcons.bits(name)
        val cell = size.minDimension / DotIcons.SIZE
        val ox = (size.width - cell * DotIcons.SIZE) / 2
        val oy = (size.height - cell * DotIcons.SIZE) / 2
        for (i in bits.indices) if (bits[i]) {
            drawCircle(
                resolvedColor,
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
    val colors = LocalDotColors.current
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
                arcs.track.forEach { (s, sw) -> drawArc(colors.track, s, sw, false, topLeft, arcSize, style = trackStroke) }
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
