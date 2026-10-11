package com.madebygps.dothabits.domain

data class GuideSection(val title: String, val paragraphs: List<String>)

object HabitGuide {
    const val summary = "Built for Nothing Phone (3) on Nothing OS 4.x / Android 16. Data stays on this phone: no internet permission, backend, account, analytics or ads."
    const val backgroundRefreshStatus = "On, about every 15 minutes; Android may delay updates"
    const val glyphProgressSetup = "Allow Dot Habits notifications and Live Updates, then enable Dot Habits in the phone's Settings > Glyph > Glyph Progress. The Timers toy still controls start, pause and timer selection."
    private const val stepSource = "Steps come only from Health Connect's own phone counter. Other apps' totals are ignored to prevent double counting. Phone recording begins when any app receives step-reading permission, so phone data may already exist before you grant Dot Habits access."
    private const val stepRefresh = "With optional background access, step refresh is scheduled about every 15 minutes while the app is closed, and every minute while it is resumed. Android may delay background work, including when the battery is low. Without background access, steps refresh when you open the app."
    private const val stepRetention = "Daily step totals are cached on this phone for offline history and streaks. You can revoke access in Health Connect at any time. The cache is shared across step habits: deleting a habit does not delete cached totals. Uninstalling the app removes its local cache."
    private const val counts = "Hold a count tile to log one completion, up to its target. Each count lights a border segment; reaching the goal makes the tile solid. Tap to open details. The detail tile shows completion progress below its icon, with schedule information outside the tile."
    private const val avoid = "Hold an avoid tile to log a slip. Its dashed outline and slip count show the allowance, not a completed day. A day succeeds while slips stay at or below the allowance; exceeding it breaks the streak immediately."
    private const val timers = "Hold a timer tile or use its corner button to start or pause. The border counts finished sessions; the interior tracks the current session and retains its level when paused. Resume continues the same planned session today. Only finishing its full duration earns automatic habit credit; paused or running partial time does not. When today's recorded work meets the daily goal, the tile becomes solid, any unfinished timer is discarded without extra credit, and timer controls disappear for the day. Starting another habit's timer pauses the previous one. Details show remaining time, not the day's elapsed total."
    private const val steps = "$stepSource Missing readings show NO STEP DATA, never an estimate. Steps cannot be manually edited. $stepRefresh"
    private const val daily = "Daily streaks count consecutive scheduled days. Rest days neither extend nor break a streak. An unmet today is pending until the day ends."
    private const val weekly = "Weekly streaks count successful weeks and reset only when a week closes below its goal. The creation week is a grace week: it can count if met but never breaks the streak. N days/week counts distinct days; several logs on one day still count as one day. N times/week counts all completions."
    private const val streaks = "CURRENT is the current run; BEST is the longest run. D means days and WK means weeks. A small home-tile flame means the current streak is above zero, not a personal best. It stays visible over empty, partially filled and solid tiles. The calendar links successful days in daily streaks, bridging rest days without counting them. For weekly habits, linked markers in the WK column show successful weeks. Past streak links are muted; current streak links are bright, with a flame on the latest successful day or week. Missed scheduled days and failed closed weeks break the links. Pending days and weeks do not earn a link."
    private const val history = "The calendar is always visible. Select today or a past date to open a day sheet with completion progress. Timer days show only completed sessions and Adjust completion, not time records. Goal met is filled, partial is outlined in the foreground colour, missed is dim outlined, pending is light outlined, and rest is neutral. The current day has a marker. Browse earlier months to see the rest of a long streak. Future dates cannot be edited; dates before tracking began remain unknown unless backfilled."
    private const val editing = "Edit the selected day's count or slips, or use Adjust completion to set its total completed timer sessions, including activity done without the timer. You can correct completions down as well as up. Meeting today's daily timer goal through a correction or a goal/duration edit discards any unfinished session without extra credit. Past-day corrections and edits that leave today below goal preserve the session's original duration and remaining time. Correcting today below goal enables a fresh timer, without restoring discarded partial time. Time records stay internal. History credit remains in seconds and is evaluated against the current session duration, so changing duration can change historical completion counts. Completed credit stays on its original date. Steps are read-only. Streaks and statistics recalculate from corrected history."
    private const val statistics = "Habit statistics is read-only and always visible. CURRENT and BEST streaks appear here alongside completion statistics. DAYS MET counts past scheduled days whose target was met. LAST 30D uses closed scheduled days in the last 30 days, or the last four finished weeks for weekly schedules. Today, the current week, rest days and the weekly creation grace period are excluded from this habit-level completion rate."
    private const val reboot = "Timers keep their own configured duration and remaining time, measured with a monotonic clock while running. At midnight, unfinished running and paused sessions are discarded, with no split, carry or partial credit. Completed sessions keep credit on their original day, even if the completion alarm arrives late. Restarting the phone discards all unfinished sessions, including paused sessions; there is no recovery review or guessed credit. Session-end and midnight alarms persist outside the app, but inexact alarms may deliver late."
    private const val toys = "Widgets and the Habit Glyph Toy are display-only. Tap a widget to open the app. Enable either or both toys through Settings > Toys > Set up; choose the Habit toy's habit under Displayed habit. Selection survives app restarts; if that habit is deleted, choose another. The Timers toy controls timers only: long-press starts or pauses, and holding at least 2 seconds switches timers on release without also toggling. Short press is Nothing's system toy cycling. Neither toy logs completions directly."
    private const val live = "Running sessions publish passive Live Update progress for Nothing's system Glyph Progress, complementary to the Timers toy. $glyphProgressSetup Pause, completion or discard removes progress; dismissal hides it for that planned session, including after resume. Android ticks the countdown, but progress refresh is best effort, at most every 15 seconds while the process is alive. Sleep or process death can pause bar refresh. Nothing OS controls visual effects; uninterrupted animation is not promised. Completion sound and vibration use the separate session-finished notification."

    val statisticsHelp = GuideSection("Overall statistics", listOf(
        "Choose 30 days, 60 days, 90 days or All time. The selected range applies to the completion rate, comparison and trend. Statistics values and labels use regular monospace lettering for consistent readability.",
        "Each past scheduled day is one goal. Each finished week is one goal for weekly habits, attributed to the week's first day. Rest days, today and the current week don't count. Unlike the habit-level LAST 30D rate, overall statistics includes closed creation weeks and backfilled history.",
        "Completion is goals met divided by measured goals. Missing step goals are excluded, never treated as zero. A weekly step goal already met by measured steps counts even if some days are missing.",
        "The comparison chip shows percentage points, not relative percent change. For example, 88% now versus 77% before is +11 points. It compares with the equally long period just before. All time has no comparison. Changes in your mix of habits can affect the overall rate.",
        "Bars group the selected range by week, or month for long ranges. Edge bars can cover only part of a week or month.",
    ))

    val privacy = GuideSection("Privacy & Health Connect", listOf(
        summary,
        "Dot Habits reads daily step totals from Health Connect to fill in step habits. Steps are read, never written, and only as daily totals.",
        stepSource,
        stepRefresh,
        stepRetention,
    ))

    val sections = listOf(
        GuideSection("Getting around", listOf(
            summary,
            "Home shows six habit tiles per page, up to 24 habits. Add a count, timer, steps or avoid habit; tap a tile for details. Use Android's back gesture or system back button to return. Tap the app icon for About, including the installed version, build details and repository link.",
            "Choose every day, selected weekdays, N distinct days/week or N times/week. The week starts Monday by default; choose Monday or Sunday in Settings. Timers use sessions times minutes, with session lengths from 1 to 300 minutes. In habit details, progress sits inside the tile beneath the icon: counts or weekly totals, slips, measured steps, or timer countdown and completed sessions. The text below the tile describes the goal and schedule; timers show the configured session count, minutes per session and scheduled days, not the current session number.",
        )),
        GuideSection("Logging & progress", listOf(counts, avoid, timers)),
        GuideSection("Schedules & streaks", listOf(daily, weekly, streaks)),
        GuideSection("Timers & reboot", listOf(reboot)),
        GuideSection("Steps & Health Connect", listOf(steps)),
        GuideSection("History & statistics", listOf(history, editing, statistics)),
        statisticsHelp,
        GuideSection("Widgets, Glyph Toys & live progress", listOf(
            "Choose Grid or Single Habit, each with Framed or Transparent styles, in the widget picker. Widgets are resizable and display-only; habit names remain available to TalkBack. App and widget progress follow the system light or dark theme and stay monochrome, as does Glyph output.",
            toys,
            live,
        )),
        GuideSection("Reminders & display", listOf(
            "Reminders notify you on scheduled days that are not done yet. Allow notifications in Settings; without exact-alarm permission, reminders and timer completion alerts may arrive late.",
            "The app follows the system theme: white text on a black background in dark mode, black text on a white background in light mode, with monochrome progress and no colour customization. Dot lettering follows system font sizing, exposes its text to TalkBack, and falls back to regular text when characters are unsupported or the scaled dots would not fit.",
            "There is no Essential Space integration or Essential Key remapping.",
        )),
        privacy,
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
