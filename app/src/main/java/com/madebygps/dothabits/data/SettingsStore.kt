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
    /** Habit shown by the display-only Habit Glyph Toy. */
    val glyphHabitId: Long? = null,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val weekStart = stringPreferencesKey("week_start")
        val glyphHabitId = longPreferencesKey("glyph_habit_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            weekStart = p[Keys.weekStart]?.let(DayOfWeek::valueOf) ?: DayOfWeek.MONDAY,
            glyphHabitId = p[Keys.glyphHabitId],
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setWeekStart(d: DayOfWeek) = context.dataStore.edit { it[Keys.weekStart] = d.name }
    suspend fun setGlyphHabit(id: Long?) = context.dataStore.edit {
        if (id == null) it.remove(Keys.glyphHabitId) else it[Keys.glyphHabitId] = id
    }
}
