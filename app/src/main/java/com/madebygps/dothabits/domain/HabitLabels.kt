package com.madebygps.dothabits.domain

import java.text.NumberFormat

/** Short, shared captions shown under rings in the app and widgets. */
object HabitLabels {
    fun streak(s: StreakStats): String = when {
        s.current <= 0 -> ""
        s.unit == StreakUnit.WEEKS -> "${s.current}W STREAK"
        else -> "${s.current}D STREAK"
    }

    fun detail(t: HabitToday): String {
        val h = t.habit
        return when {
            h.type == HabitType.STEPS ->
                if (!t.hasData) "NO STEP DATA" else "${NumberFormat.getIntegerInstance().format(t.value)} STEPS"
            h.type == HabitType.TIMED ->
                "${TimerMath.formatDuration(t.value)} / ${TimerMath.formatDuration(h.dailyGoalUnits)}".uppercase()
            h.isNegative -> if (t.value == 1L) "1 SLIP" else "${t.value} SLIPS"
            t.week != null -> "${t.week.first}/${t.week.second} THIS WEEK"
            t.status == TodayStatus.REST -> "REST DAY"
            h.dailyTarget > 1 -> "${t.value}/${h.dailyTarget} TODAY"
            else -> ""
        }
    }

    /** One line: detail plus streak when both exist. */
    fun caption(t: HabitToday): String =
        listOf(detail(t), streak(t.streak)).filter { it.isNotEmpty() }.joinToString(" · ")
}
