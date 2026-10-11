package com.madebygps.dothabits.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test fun foregroundAndBackgroundFollowSystemTheme() {
        for (dark in listOf(false, true)) {
            val scheme = dotColorScheme(dark)
            val foreground = if (dark) Color.White else Color.Black
            val background = if (dark) Color.Black else Color.White
            assertEquals(background, scheme.background)
            assertEquals(foreground, scheme.onBackground)
            assertEquals(foreground, scheme.primary)
            assertEquals(background, scheme.onPrimary)
            assertEquals(background, scheme.inverseOnSurface)
            assertEquals(foreground, scheme.inverseSurface)
        }
    }

    @Test fun allMaterialAndTileColorsAreMonochrome() {
        for (dark in listOf(false, true)) {
            val scheme = dotColorScheme(dark)
            val tiles = if (dark) DarkDotColors else LightDotColors
            val colors = listOf(
                scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer,
                scheme.inversePrimary, scheme.secondary, scheme.onSecondary, scheme.secondaryContainer,
                scheme.onSecondaryContainer, scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer,
                scheme.onTertiaryContainer, scheme.background, scheme.onBackground, scheme.surface,
                scheme.onSurface, scheme.surfaceVariant, scheme.onSurfaceVariant, scheme.surfaceTint,
                scheme.inverseSurface, scheme.inverseOnSurface, scheme.error, scheme.onError,
                scheme.errorContainer, scheme.onErrorContainer, scheme.outline, scheme.outlineVariant,
                scheme.scrim, scheme.surfaceBright, scheme.surfaceDim, scheme.surfaceContainer,
                scheme.surfaceContainerLow, scheme.surfaceContainerLowest, scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest, scheme.primaryFixed, scheme.primaryFixedDim,
                scheme.onPrimaryFixed, scheme.onPrimaryFixedVariant, scheme.secondaryFixed,
                scheme.secondaryFixedDim, scheme.onSecondaryFixed, scheme.onSecondaryFixedVariant,
                scheme.tertiaryFixed, scheme.tertiaryFixedDim, scheme.onTertiaryFixed,
                scheme.onTertiaryFixedVariant, tiles.background, tiles.surface, tiles.track,
                tiles.line, tiles.text, tiles.muted, tiles.dim,
            )
            colors.forEach {
                assertEquals(it.red, it.green, 0f)
                assertEquals(it.red, it.blue, 0f)
            }
        }
    }

    @Test fun foregroundAndBackgroundHaveMaximumContrastInBothThemes() {
        for (colors in listOf(DarkDotColors, LightDotColors)) {
            val lighter = maxOf(colors.text.luminance(), colors.background.luminance())
            val darker = minOf(colors.text.luminance(), colors.background.luminance())
            assertEquals(21f, (lighter + .05f) / (darker + .05f), .0001f)
        }
    }
}
