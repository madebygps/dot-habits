package com.madebygps.dothabits.domain

data class GuideSection(val title: String, val paragraphs: List<String>)

object HabitGuide {
    private const val counts = "Hold a count tile to log one completion, up to its target. Each count lights a border segment; reaching the goal makes the tile solid. Tap to open details."
    private const val avoid = "Hold an avoid tile to log a slip. Its dashed outline and slip count show the allowance, not a completed day. A day succeeds while slips stay at or below the allowance; exceeding it breaks the streak immediately."
    private const val timers = "Hold a timer tile or use its corner button to start or pause. The border counts finished sessions; the interior tracks the current session and retains its level when paused. Each run stops at its stored session limit. Details show remaining time, not the day's elapsed total."
    private const val steps = "Steps update automatically from Health Connect's own phone counter. Other apps' totals are ignored to prevent double counting. Counting begins after permission is granted; missing readings show NO STEP DATA, never an estimate. Steps cannot be manually edited. Refresh is about every 15 minutes in the background and every minute while the app is resumed."
    private const val daily = "Daily streaks count consecutive scheduled days. Rest days neither extend nor break a streak. An unmet today is pending until the day ends."
    private const val weekly = "Weekly streaks count successful weeks and reset only when a week closes below its goal. The creation week is a grace week: it can count if met but never breaks the streak. N days/week counts distinct days; several logs on one day still count as one day. N times/week counts all completions."
    private const val streaks = "CURRENT is the current run; BEST is the longest run. D means days and WK means weeks. A small home-tile dot means the current streak is above zero, not a personal best. The recent chain shows seven calendar days or seven weeks, including the current period."
    private const val history = "Expand Calendar and select today or a past date to open a day sheet with completion progress. Timer days show only completed sessions and Adjust completion, not time records. Goal met is filled, partial is outlined in the highlight colour, missed is dim outlined, pending is light outlined, and rest is neutral. The current period has a marker. Future dates cannot be edited; dates before tracking began remain unknown unless backfilled."
    private const val editing = "Edit the selected day's count or slips, or use Adjust completion to set its total completed timer sessions, including activity done without the timer. You can correct completions down as well as up. Partial-session progress is retained; adjusting a running timer never starts, stops or changes its countdown. Time records stay internal and cannot be edited or deleted in Calendar; interrupted runs are the review exception. Use EDIT to change session duration; a running session keeps its original limit. History credit remains in seconds and is evaluated against the current session duration, so changing duration can change historical completion counts. Midnight credit stays on each actual date. Steps are read-only. Streaks and statistics recalculate from corrected history."
    private const val statistics = "Statistics is read-only. DAYS MET counts past scheduled days whose target was met. LAST 30D uses closed scheduled days in the last 30 days, or the last four finished weeks for weekly schedules. Today, the current week, rest days and the weekly creation grace period are excluded from completion rates."
    private const val reboot = "Timers use a monotonic clock within one boot and wall-clock dates to split time at midnight. After a reboot, interrupted sessions need review and count only up to the last confirmed-alive time. Keep that time, extend to restart within the run limit, or discard; nothing is guessed."
    private const val toys = "Widgets and the Habit Glyph Toy are display-only. Tap a widget to open the app; choose the Habit toy's habit in Settings. The Timers toy controls timers only: long-press starts or pauses, and holding at least 2 seconds switches timers on release without also toggling. Short press is Nothing's system toy cycling. Neither toy logs completions directly."
    private const val live = "Running sessions publish passive Live Update progress for Nothing's system Glyph Progress, complementary to the Timers toy. Allow notifications and Live Updates, then enable Dot Habits in system Glyph Progress settings. Pause, completion or review removes progress; dismissal hides it for that run. Android ticks the countdown, but progress refresh is best effort, at most every 15 seconds while the process is alive. Sleep or process death can pause bar refresh. Nothing OS controls visual effects; uninterrupted animation is not promised. Completion sound and vibration use the separate session-finished notification."

    val sections = listOf(
        GuideSection("Logging & progress", listOf(counts, avoid, timers)),
        GuideSection("Schedules & streaks", listOf(daily, weekly, streaks)),
        GuideSection("Timers & reboot", listOf(reboot)),
        GuideSection("Steps & Health Connect", listOf(steps)),
        GuideSection("History & statistics", listOf(history, editing, statistics)),
        GuideSection("Widgets, Glyph Toys & live progress", listOf(toys, live)),
        GuideSection("Local-first", listOf("Dot Habits is built for Nothing Phone (3). Data stays on this phone: no internet permission, backend, account, analytics or ads. No Essential Space integration or Essential Key remapping.")),
    )

    fun contextual(h: Habit): List<GuideSection> = listOf(
        GuideSection("Progress", listOf(when {
            h.isNegative -> avoid
            h.type == HabitType.TIMED -> timers
            h.type == HabitType.STEPS -> steps
            else -> counts
        })),
        GuideSection("Streaks", listOf(if (h.schedule.isWeekly) weekly else daily, streaks)),
        GuideSection("Calendar & statistics", listOf(history, editing, statistics)),
    )

    val toyHelp = GuideSection("Glyph Toys & progress", listOf(toys, live))
}
