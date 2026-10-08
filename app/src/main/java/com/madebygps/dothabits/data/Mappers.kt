package com.madebygps.dothabits.data

import com.madebygps.dothabits.domain.Entry
import com.madebygps.dothabits.domain.Habit
import com.madebygps.dothabits.domain.HabitType
import com.madebygps.dothabits.domain.Schedule
import com.madebygps.dothabits.domain.ScheduleKind
import com.madebygps.dothabits.domain.SessionState
import com.madebygps.dothabits.domain.TimerSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

fun HabitEntity.toDomain() = Habit(
    id = id,
    name = name,
    icon = icon,
    type = HabitType.valueOf(type),
    dailyTarget = dailyTarget,
    schedule = Schedule(
        kind = ScheduleKind.valueOf(scheduleKind),
        weekdays = DayOfWeek.entries.filter { weekdaysMask and (1 shl (it.value - 1)) != 0 }.toSet(),
        perWeek = perWeek,
    ),
    isNegative = negative,
    reminders = reminders.split(',').filter { it.isNotBlank() }.map { LocalTime.parse(it) }.sorted(),
    position = position,
    createdOn = LocalDate.ofEpochDay(createdOnEpochDay),
    sessions = sessions.coerceAtLeast(1),
)

fun Habit.toEntity() = HabitEntity(
    id = id,
    name = name,
    icon = icon,
    type = type.name,
    dailyTarget = dailyTarget,
    scheduleKind = schedule.kind.name,
    weekdaysMask = schedule.weekdays.fold(0) { m, d -> m or (1 shl (d.value - 1)) },
    perWeek = schedule.perWeek,
    negative = isNegative,
    reminders = reminders.sorted().joinToString(",") { "%02d:%02d".format(it.hour, it.minute) },
    position = position,
    createdOnEpochDay = createdOn.toEpochDay(),
    sessions = sessions,
)

fun EntryEntity.toDomain() = Entry(id, habitId, LocalDate.ofEpochDay(epochDay), amount, Instant.ofEpochMilli(createdAtMs))

fun TimerSessionEntity.toDomain() = TimerSession(
    id = id,
    habitId = habitId,
    start = Instant.ofEpochMilli(startMs),
    end = endMs?.let(Instant::ofEpochMilli),
    state = SessionState.valueOf(state),
    lastAlive = Instant.ofEpochMilli(lastAliveMs),
    bootCount = bootCount,
    limitSeconds = limitSeconds,
)
