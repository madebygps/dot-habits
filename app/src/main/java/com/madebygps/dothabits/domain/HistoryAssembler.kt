package com.madebygps.dothabits.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns raw stored rows into per-habit day values. Shared by every surface, so the app,
 * widgets and Glyph Toy always agree on what "today" looks like.
 */
object HistoryAssembler {
    fun assemble(
        habits: List<Habit>,
        entries: List<Entry>,
        sessions: List<TimerSession>,
        stepsByDay: Map<LocalDate, Long>,
        today: LocalDate,
        zone: ZoneId,
        now: Instant,
    ): List<HabitHistory> {
        val entriesByHabit = entries.groupBy { it.habitId }
        val sessionsByHabit = sessions.groupBy { it.habitId }
        return habits.map { habit ->
            val values = HashMap<LocalDate, Long>()
            when (habit.type) {
                HabitType.STEPS -> values.putAll(stepsByDay)
                else -> {
                    entriesByHabit[habit.id].orEmpty().forEach { values.merge(it.date, it.amount, Long::plus) }
                    if (habit.type == HabitType.TIMED) {
                        TimerMath.secondsByDay(sessionsByHabit[habit.id].orEmpty(), zone, now)
                            .forEach { (d, s) -> values.merge(d, s, Long::plus) }
                    }
                }
            }
            // Timer offsets may be negative: clamp only after combining with recorded credit.
            values.replaceAll { _, v -> v.coerceAtLeast(0L) }
            HabitHistory(
                habit = habit,
                values = values,
                sessions = sessionsByHabit[habit.id].orEmpty(),
                stepsAvailableToday = habit.type != HabitType.STEPS || stepsByDay.containsKey(today),
            )
        }
    }
}
