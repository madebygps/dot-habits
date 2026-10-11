package com.madebygps.dothabits.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.madebygps.dothabits.domain.HabitLabels
import com.madebygps.dothabits.domain.HabitToday

internal object WidgetColors {
    val background = dayNight(Color.White, Color.Black)
    val foreground = dayNight(Color.Black, Color.White)
    val track = dayNight(Color(0xFF9A9A9A), Color(0xFF454545))
    val dim = dayNight(Color(0xFF666666), Color(0xFF8A8A8A))

    private fun dayNight(day: Color, night: Color): GlanceColorProvider = ColorProvider(day, night)

    fun tone(tone: RingBitmaps.Tone): GlanceColorProvider = when (tone) {
        RingBitmaps.Tone.FOREGROUND -> foreground
        RingBitmaps.Tone.HIGHLIGHT -> foreground
        RingBitmaps.Tone.DIM -> dim
        RingBitmaps.Tone.BACKGROUND -> background
    }
}

@Composable
internal fun HabitRingImage(t: HabitToday, size: Dp, sizePx: Int) {
    val layers = RingBitmaps.layers(t, sizePx)
    Box(GlanceModifier.size(size)) {
        Image(
            ImageProvider(layers.fill),
            null,
            modifier = GlanceModifier.size(size),
            colorFilter = ColorFilter.tint(WidgetColors.tone(layers.progressTone)),
        )
        Image(
            ImageProvider(layers.track),
            null,
            modifier = GlanceModifier.size(size),
            colorFilter = ColorFilter.tint(WidgetColors.track),
        )
        Image(
            ImageProvider(layers.progress),
            null,
            modifier = GlanceModifier.size(size),
            colorFilter = ColorFilter.tint(WidgetColors.tone(layers.progressTone)),
        )
        Image(
            ImageProvider(layers.icon),
            HabitLabels.accessibility(t),
            modifier = GlanceModifier.size(size),
            colorFilter = ColorFilter.tint(WidgetColors.tone(layers.iconTone)),
        )
    }
}
