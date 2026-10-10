package com.madebygps.dothabits.domain

import java.time.LocalDate

/** What a press-and-hold on a habit tile does. Never used by widgets or the Glyph Toy. */
enum class HoldAction { LOG_ONE, TOGGLE_TIMER, NONE_ALREADY_DONE, NONE_AUTOMATIC }

object CompletionPolicy {
    fun timerGoalMet(habit: Habit, completedSeconds: Long): Boolean =
        habit.type == HabitType.TIMED && completedSeconds >= habit.dailyGoalUnits

    fun shouldDiscardTimer(habit: Habit, completedSeconds: Long, session: TimerSession, today: LocalDate): Boolean =
        session.habitId == habit.id && session.state != SessionState.CLOSED &&
            session.epochDay == today.toEpochDay() && timerGoalMet(habit, completedSeconds)

    fun holdAction(t: HabitToday): HoldAction = when {
        t.habit.type == HabitType.STEPS -> HoldAction.NONE_AUTOMATIC
        t.habit.type == HabitType.TIMED ->
            if (t.canControlTimer) HoldAction.TOGGLE_TIMER else HoldAction.NONE_ALREADY_DONE
        t.habit.isNegative -> HoldAction.LOG_ONE
        t.habit.schedule.kind == ScheduleKind.TIMES_PER_WEEK ->
            if ((t.week?.first ?: 0) < (t.week?.second ?: 0)) HoldAction.LOG_ONE else HoldAction.NONE_ALREADY_DONE
        // DAYS_PER_WEEK: one completion per day at most (distinct days), independent of the weekly total.
        else -> if (t.value < t.habit.dailyGoalUnits) HoldAction.LOG_ONE else HoldAction.NONE_ALREADY_DONE
    }
}
