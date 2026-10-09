package com.madebygps.dothabits.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.dataStore by preferencesDataStore("settings")

data class AppSettings(
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** ARGB highlight used by the app and widgets. The Glyph stays monochrome. */
    val highlight: Long = HighlightPalette.first().argb,
    /** Habit shown by the display-only Habit Glyph Toy. */
    val glyphHabitId: Long? = null,
)

data class Highlight(val name: String, val argb: Long)

/** Original palette. Red is the agreed default. */
val HighlightPalette = listOf(
    Highlight("Signal red", 0xFFE8343AL),
    Highlight("Amber", 0xFFF2A33AL),
    Highlight("Acid", 0xFFC8F03CL),
    Highlight("Mint", 0xFF4BD6A0L),
    Highlight("Ice", 0xFF5AB8F5L),
    Highlight("Violet", 0xFFA78BFAL),
    Highlight("Paper", 0xFFEDEDEDL),
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val weekStart = stringPreferencesKey("week_start")
        val highlight = longPreferencesKey("highlight")
        val glyphHabitId = longPreferencesKey("glyph_habit_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            weekStart = p[Keys.weekStart]?.let(DayOfWeek::valueOf) ?: DayOfWeek.MONDAY,
            highlight = p[Keys.highlight] ?: HighlightPalette.first().argb,
            glyphHabitId = p[Keys.glyphHabitId],
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setWeekStart(d: DayOfWeek) = context.dataStore.edit { it[Keys.weekStart] = d.name }
    suspend fun setHighlight(argb: Long) = context.dataStore.edit { it[Keys.highlight] = argb }
    suspend fun setGlyphHabit(id: Long?) = context.dataStore.edit {
        if (id == null) it.remove(Keys.glyphHabitId) else it[Keys.glyphHabitId] = id
    }
}
