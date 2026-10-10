package com.madebygps.dothabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Snapshot building, hold policy and reminders: the shared "today" seen by app, widget and Glyph. */
class SnapshotAndPolicyTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 3) // Wednesday
    private val now = today.atTime(18, 0).atZone(zone).toInstant()
    private val created = LocalDate.of(2026, 6, 1)

    private val creatine = Habit(1, "Creatine", "scoop", HabitType.COUNT, 1, createdOn = created, position = 0)
    private val workout = Habit(2, "Workout", "dumbbell", HabitType.COUNT, 1, Schedule.daysPerWeek(4), createdOn = created, position = 1)
    private val walk = Habit(3, "Walk", "shoe", HabitType.STEPS, 10_000, createdOn = created, position = 2)
    private val read = Habit(4, "Read", "book", HabitType.TIMED, 60, createdOn = created, position = 3)
    private val meds = Habit(5, "Chonk Meds", "paw", HabitType.COUNT, 2, createdOn = created, position = 4)
    private val tuesdays = Habit(6, "Tue", "dot", HabitType.COUNT, 1, Schedule.weekdays(TUESDAY), createdOn = created, position = 5)

    private fun entry(h: Habit, d: LocalDate, n: Long = 1) = Entry(0, h.id, d, n, now)

    private fun snapshot(
        entries: List<Entry> = emptyList(),
        sessions: List<TimerSession> = emptyList(),
        steps: Map<LocalDate, Long> = emptyMap(),
        habits: List<Habit> = listOf(creatine, workout, walk, read, meds, tuesdays),
    ): TodaySnapshot {
        val histories = HistoryAssembler.assemble(habits, entries, sessions, steps, today, zone, now)
        return SnapshotBuilder.build(histories, today, MONDAY, now)
    }

    @Test fun segmentedMedsRingAndPartialProgress() {
        val s = snapshot(listOf(entry(meds, today)))
        val m = s.habits.first { it.habit.id == meds.id }
        assertEquals(2, m.segments)
        assertEquals(0.5f, m.fraction, 0.001f)
        assertEquals(TodayStatus.IN_PROGRESS, m.status)
        assertEquals("1/2 TODAY", HabitLabels.detail(m))
        assertEquals("", HabitLabels.caption(m)) // progress is in the ring; no streak yet
    }

    @Test fun restDayIsExcludedFromDueCount() {
        val s = snapshot()
        assertEquals(TodayStatus.REST, s.habits.first { it.habit.id == tuesdays.id }.status)
        assertEquals(5, s.dueCount)
    }

    @Test fun stepsWithoutDataAreNeverFaked() {
        val s = snapshot()
        val w = s.habits.first { it.habit.id == walk.id }
        assertEquals(false, w.hasData)
        assertEquals(0f, w.fraction, 0f)
        assertEquals("NO STEP DATA", HabitLabels.detail(w))
        assertEquals("NO STEP DATA", HabitLabels.caption(w))
        val withData = snapshot(steps = mapOf(today to 10_250L)).habits.first { it.habit.id == walk.id }
        assertEquals(TodayStatus.DONE, withData.status)
    }

    @Test fun timedHabitCombinesSessionsAndManualTime() {
        val start = today.atTime(17, 30).atZone(zone).toInstant()
        val sessions = listOf(TimerSession(1, read.id, start, null, SessionState.RUNNING, start, 1))
        val s = snapshot(listOf(entry(read, today, 15 * 60L)), sessions)
        val r = s.habits.first { it.habit.id == read.id }
        assertEquals(45 * 60L, r.value)
        assertTrue(r.timerRunning)
        assertEquals(read.id, s.activeTimer?.habitId)
        assertEquals(45 * 60L, s.activeTimer?.todaySeconds)
    }

    @Test fun workoutWeekDoneMarksTodayDone() {
        val days = listOf(created, created.plusDays(1))
        val partial = snapshot(days.map { entry(workout, it) }).habits.first { it.habit.id == workout.id }
        assertEquals(2 to 4, partial.week)
        assertEquals(TodayStatus.NOT_STARTED, partial.status)
        assertEquals(HoldAction.LOG_ONE, CompletionPolicy.holdAction(partial))
        val doneToday = snapshot((days + today).map { entry(workout, it) }).habits.first { it.habit.id == workout.id }
        // Already done today: a second hold must not count as another distinct day.
        assertEquals(HoldAction.NONE_ALREADY_DONE, CompletionPolicy.holdAction(doneToday))
    }

    @Test fun holdPolicyPerType() {
        val s = snapshot(listOf(entry(creatine, today)))
        fun of(h: Habit) = CompletionPolicy.holdAction(s.habits.first { it.habit.id == h.id })
        assertEquals(HoldAction.NONE_ALREADY_DONE, of(creatine))
        assertEquals(HoldAction.LOG_ONE, of(meds))
        assertEquals(HoldAction.TOGGLE_TIMER, of(read))
        assertEquals(HoldAction.NONE_AUTOMATIC, of(walk))
    }

    @Test fun overallProgressCounts() {
        val s = snapshot(listOf(entry(creatine, today), entry(meds, today, 2)))
        assertEquals(2, s.doneCount)
        assertEquals(0.4f, s.overallFraction, 0.001f)
    }

    @Test fun reminderPlannerSkipsUnscheduledDaysAndPastTimes() {
        val h = tuesdays.copy(reminders = listOf(LocalTime.of(9, 0)))
        // Wednesday 18:00 -> next Tuesday 09:00
        assertEquals(LocalDateTime.of(2026, 6, 9, 9, 0), ReminderPlanner.next(listOf(h), today.atTime(18, 0)))
        val daily = creatine.copy(reminders = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        assertEquals(today.atTime(20, 0), ReminderPlanner.next(listOf(h, daily), today.atTime(18, 0)))
        assertNull(ReminderPlanner.next(listOf(creatine), today.atTime(18, 0)))
    }

    @Test fun reminderNotDueWhenAlreadyDone() {
        val withReminder = Habit(1, "Creatine", "scoop", HabitType.COUNT, 1, reminders = listOf(LocalTime.of(18, 0)), createdOn = created)
        val h = HistoryAssembler.assemble(listOf(withReminder), listOf(entry(creatine, today)), emptyList(), emptyMap(), today, zone, now)
        val s = SnapshotBuilder.build(h, today, MONDAY, now)
        assertTrue(ReminderPlanner.due(s, LocalTime.of(18, 0)).isEmpty())
        val h2 = HistoryAssembler.assemble(listOf(withReminder), emptyList(), emptyList(), emptyMap(), today, zone, now)
        assertEquals(1, ReminderPlanner.due(SnapshotBuilder.build(h2, today, MONDAY, now), LocalTime.of(18, 0)).size)
    }

    @Test fun historicalEditFlowsIntoTodayStreak() {
        val base = listOf(entry(creatine, created), entry(creatine, today))
        assertEquals(1, snapshot(base).habits.first().streak.current)
        val backfilled = base + entry(creatine, created.plusDays(1))
        assertEquals(3, snapshot(backfilled).habits.first().streak.current)
    }

    @Test fun dotIconsAreWellFormed() {
        assertTrue(DotIcons.validate().isEmpty())
    }

    @Test fun homeStreakIsOnlyAMarkerWithAccessibleUnits() {
        val base = snapshot().habits.first { it.habit.id == creatine.id }
        val active = base.copy(streak = StreakStats(12, 20, StreakUnit.WEEKS), week = 2 to 4)
        assertEquals("", HabitLabels.caption(active))
        assertTrue(HabitLabels.hasStreakMarker(active))
        assertTrue(HabitLabels.accessibility(active).contains("12 week streak"))
        assertEquals("", HabitLabels.caption(base.copy(streak = StreakStats(0, 34, StreakUnit.DAYS))))
        assertEquals("", HabitLabels.caption(base.copy(status = TodayStatus.REST, streak = StreakStats(0, 3, StreakUnit.DAYS))))
        val rest = base.copy(status = TodayStatus.REST, streak = StreakStats(5, 5, StreakUnit.DAYS))
        assertEquals("", HabitLabels.caption(rest))
        assertTrue(HabitLabels.hasStreakMarker(rest))
        assertEquals(false, HabitLabels.hasStreakMarker(base.copy(streak = StreakStats(0, 34, StreakUnit.DAYS))))
        assertEquals("NEEDS REVIEW", HabitLabels.caption(active.copy(needsReview = true)))
    }

    @Test fun runningTimerCaptionShowsSessionCountdown() {
        val r = snapshot().habits.first { it.habit.id == read.id }
            .copy(value = 23 * 60L, timerRunning = true, streak = StreakStats(4, 4, StreakUnit.DAYS))
        assertEquals("37:00 LEFT", HabitLabels.caption(r))
    }

    private val deepWork = Habit(7, "Deep Work", "dot", HabitType.TIMED, 25, sessions = 4, createdOn = created, position = 6)

    @Test fun timedSessionsSplitTheRingAndGoal() {
        assertEquals(100 * 60L, deepWork.dailyGoalUnits)
        assertEquals(4, deepWork.ringSegments)
        assertEquals(0, read.ringSegments)
        val s = snapshot(listOf(entry(deepWork, today, 50 * 60L)), habits = listOf(deepWork))
        val d = s.habits.single()
        assertEquals(0.5f, d.fraction, 0.001f)
        assertEquals(25 * 60L, d.sessionRemaining)
        assertEquals("2/4 SESSIONS · 50M", HabitLabels.detail(d))
        assertEquals(TodayStatus.IN_PROGRESS, d.status)
    }

    @Test fun activeTimerIsRunningOneElseFirstUnfinishedTimedHabit() {
        // Nothing running: the first timed habit due and not done (home order).
        val idle = snapshot(listOf(entry(read, today, 60 * 60L)), habits = listOf(read, deepWork))
        assertEquals(deepWork.id, idle.activeTimer?.habitId)
        assertEquals(false, idle.activeTimer?.running)
        // A running timer always wins.
        val start = today.atTime(17, 50).atZone(zone).toInstant()
        val run = TimerSession(1, read.id, start, null, SessionState.RUNNING, start, 1, limitSeconds = 60 * 60L)
        val running = snapshot(sessions = listOf(run), habits = listOf(read, deepWork))
        assertEquals(read.id, running.activeTimer?.habitId)
        assertEquals(50 * 60L, running.activeTimer?.sessionRemaining)
        // Everything done: no target.
        val done = snapshot(listOf(entry(read, today, 3600L), entry(deepWork, today, 6000L)), habits = listOf(read, deepWork))
        assertNull(done.activeTimer)
    }

    @Test fun expiredRunIsNoLongerRunning() {
        val start = today.atTime(17, 0).atZone(zone).toInstant()
        val run = TimerSession(1, deepWork.id, start, null, SessionState.RUNNING, start, 1, limitSeconds = 25 * 60L)
        val d = snapshot(sessions = listOf(run), habits = listOf(deepWork)).habits.single()
        assertEquals(false, d.timerRunning)
        assertEquals(25 * 60L, d.value)
    }
}
