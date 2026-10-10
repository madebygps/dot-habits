package com.madebygps.dothabits.domain

import java.text.NumberFormat

/** Short, shared captions shown under habit tiles in the app and widgets. */
object HabitLabels {
    fun accessibility(t: HabitToday): String = listOf(
        t.habit.name, detail(t), caption(t), streak(t.streak), t.status.name.lowercase().replace('_', ' '),
    ).filter { it.isNotEmpty() }.distinct().joinToString(". ")

    fun streak(s: StreakStats): String = when {
        s.current <= 0 -> ""
        s.unit == StreakUnit.WEEKS -> "${s.current} week streak"
        else -> "${s.current} day streak"
    }

    fun detail(t: HabitToday): String {
        val h = t.habit
        return when {
            h.type == HabitType.STEPS ->
                if (!t.hasData) "NO STEP DATA" else "${NumberFormat.getIntegerInstance().format(t.value)} STEPS"
            h.type == HabitType.TIMED && h.sessions > 1 ->
                "${TimerMath.sessionsDone(t.value, h.sessionSeconds, h.sessions)}/${h.sessions} SESSIONS · ${TimerMath.formatDuration(t.value)}".uppercase()
            h.type == HabitType.TIMED ->
                "${TimerMath.formatDuration(t.value)} / ${TimerMath.formatDuration(h.dailyGoalUnits)}".uppercase()
            h.isNegative -> "${t.value} ${if (t.value == 1L) "SLIP" else "SLIPS"} / ${h.dailyTarget} ALLOWED"
            t.week != null -> "${t.week.first}/${t.week.second} THIS WEEK"
            t.status == TodayStatus.REST -> "REST DAY"
            h.dailyTarget > 1 -> "${t.value}/${h.dailyTarget} TODAY"
            else -> ""
        }
    }

    /** Only essential state captions; a single in-tile dot represents an active streak. */
    fun caption(t: HabitToday): String = when {
        t.needsReview -> "NEEDS REVIEW"
        t.habit.type == HabitType.STEPS && !t.hasData -> "NO STEP DATA"
        t.habit.isNegative -> "${t.value}/${t.habit.dailyTarget} SLIPS" +
            if (t.status == TodayStatus.SLIPPED) " · SLIPPED" else ""
        t.timerRunning -> "${TimerMath.formatClock(t.tileSessionProgress?.remainingSeconds ?: t.sessionRemaining)} LEFT"
        else -> ""
    }

    fun hasStreakMarker(t: HabitToday): Boolean = t.streak.current > 0

    fun holdAction(t: HabitToday): String? = when (CompletionPolicy.holdAction(t)) {
        HoldAction.LOG_ONE -> if (t.habit.isNegative) "Log a slip" else "Log one completion"
        HoldAction.TOGGLE_TIMER -> if (t.timerRunning) "Pause timer" else "Start timer"
        HoldAction.NONE_ALREADY_DONE -> "Already complete"
        HoldAction.NONE_AUTOMATIC -> null
    }
}
