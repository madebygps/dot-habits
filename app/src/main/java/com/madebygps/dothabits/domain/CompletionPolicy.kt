package com.madebygps.dothabits.domain

/** What a press-and-hold on a habit circle does. Never used by widgets or the Glyph Toy. */
enum class HoldAction { LOG_ONE, TOGGLE_TIMER, NONE_ALREADY_DONE, NONE_AUTOMATIC }

object CompletionPolicy {
    fun holdAction(t: HabitToday): HoldAction = when {
        t.habit.type == HabitType.STEPS -> HoldAction.NONE_AUTOMATIC
        t.habit.type == HabitType.TIMED -> HoldAction.TOGGLE_TIMER
        t.habit.isNegative -> HoldAction.LOG_ONE
        t.habit.schedule.kind == ScheduleKind.TIMES_PER_WEEK ->
            if ((t.week?.first ?: 0) < (t.week?.second ?: 0)) HoldAction.LOG_ONE else HoldAction.NONE_ALREADY_DONE
        // DAYS_PER_WEEK: one completion per day at most (distinct days), independent of the weekly total.
        else -> if (t.value < t.habit.dailyGoalUnits) HoldAction.LOG_ONE else HoldAction.NONE_ALREADY_DONE
    }
}
