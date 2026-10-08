package com.madebygps.dothabits.domain

import java.time.LocalDateTime
import java.time.LocalTime

/** Reminder planning: one alarm is kept for the next reminder moment across all habits. */
object ReminderPlanner {

    fun next(habits: List<Habit>, after: LocalDateTime): LocalDateTime? =
        habits.flatMap { h -> h.reminders.mapNotNull { t -> nextFor(h, t, after) } }.minOrNull()

    private fun nextFor(h: Habit, t: LocalTime, after: LocalDateTime): LocalDateTime? {
        var day = after.toLocalDate()
        repeat(8) {
            val candidate = day.atTime(t)
            if (candidate.isAfter(after) && h.schedule.isScheduledOn(day) && !day.isBefore(h.createdOn)) return candidate
            day = day.plusDays(1)
        }
        return null
    }

    /** Habits whose reminder is at [time] (minute precision) and that still need attention today. */
    fun due(snapshot: TodaySnapshot, time: LocalTime): List<Habit> =
        snapshot.habits.filter { t ->
            val matches = t.habit.reminders.any { it.hour == time.hour && it.minute == time.minute }
            val needsAttention = if (t.habit.isNegative) true else t.status != TodayStatus.DONE
            matches && t.status != TodayStatus.REST && needsAttention
        }.map { it.habit }
}
