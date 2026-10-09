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
import com.madebygps.dothabits.domain.HabitToday

internal object WidgetColors {
    val background = dayNight(Color(0xD9F1F1F1), Color(0xD91B1B1B))
    val foreground = dayNight(Color.Black, Color(0xFFF2F2F2))
    val track = dayNight(Color(0xFF9A9A96), Color(0xFF454545))
    val dim = dayNight(Color(0xFF666663), Color(0xFF8A8A8A))

    private fun dayNight(day: Color, night: Color): GlanceColorProvider = ColorProvider(day, night)

    fun tone(tone: RingBitmaps.Tone): GlanceColorProvider = when (tone) {
        RingBitmaps.Tone.FOREGROUND -> foreground
        RingBitmaps.Tone.DIM -> dim
        RingBitmaps.Tone.BACKGROUND -> background
    }
}

@Composable
internal fun HabitRingImage(t: HabitToday, size: Dp, sizePx: Int) {
    val layers = RingBitmaps.layers(t, sizePx)
    Box(GlanceModifier.size(size)) {
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
            t.habit.name,
            modifier = GlanceModifier.size(size),
            colorFilter = ColorFilter.tint(WidgetColors.tone(layers.iconTone)),
        )
    }
}
