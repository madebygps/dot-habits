# Dot Habits

A habit tracker for **Nothing Phone (3)** (Nothing OS 4.1 / Android 16), inspired by [Streaks](https://streaksapp.com/).
Black UI, dot-matrix icons, one highlight colour. Works fully offline, with no account and no `INTERNET` permission.

## Features

- **Home:** six rounded-square habit tiles per page (2×3), up to 24 habits. Hold to log a count or start/pause a timer; tap for details. Steps update automatically.
- **Progress:** count borders light one segment per logged count; timer borders count finished sessions while the interior fills bottom-up within the current session and retains its level when paused. Finishing a session lights its border segment and resets the interior for the next one. Steps fill toward the daily goal. Reached goals become solid with inverted icons; avoid habits keep a dashed outline and show slips/allowance rather than a success fill.
- **About:** tap the centered app icon for the installed version, creator credit, and GitHub repo.
- **Habit types:** check/count (e.g. meds 2×/day), timer (sessions × minutes, e.g. Deep Work 4 × 25), steps, and avoid.
- **Schedules:** every day, selected weekdays, N distinct days/week, or N times/week. The week starts Monday by default.
- **Streaks:** a single muted dot inside a home tile marks a nonzero current streak (no numeric caption or personal-best marker). Detail shows compact Current/Best values and a seven-day or seven-week dot chain, with truthful rest, partial, pending and current-period states. Daily streaks skip unscheduled days; weekly streaks reset only when a week closes below their goal.
- **Detail:** a centered hero keeps tile progress and timer controls. Timers show only current-session remaining time plus session position/duration. Interrupted timers keep a prominent review card, the only place timer timestamps are exposed.
- **History:** expand Calendar and tap today or a past date for completion progress. Timer days show only completed-session count (for example, “1 of 2 sessions complete”) and **Adjust completion**. Set the absolute integer count, up or down, including activity done without the timer. No manual-minutes editor, session list or session deletion. Count/slip corrections remain available; steps stay read-only.
- **Timer corrections:** existing entries store a signed seconds offset from internal recorded credit. Saving replaces that day's offset with `completedSessions × sessionSeconds + creditedRemainder − recordedSeconds`, preserving fractional progress without double counting or deleting runs. Running corrections leave timestamps, monotonic anchors, limits and deadlines untouched, even when the corrected daily goal is met; the run still ends at its own limit. Midnight credit and corrections stay on their respective dates. All surfaces, streaks and statistics use corrected history. No schema change is needed. As with recorded credit, history remains in seconds and is evaluated against the current session duration; changing duration can change historical completion counts, but never the active run's limit. Interrupted runs can still be reviewed or discarded explicitly.
- **Habit statistics:** collapsed and read-only, with past scheduled days met and recent closed-opportunity completion rate. Current/Best remain visible above the recent chain.
- **Help:** contextual `?` links to the shared **Settings › How Dot Habits works** guide, covering progress, schedules, streaks, timers, steps, history and display controls.
- **Statistics:** 30/60/90-day or all-time completion rate, weekly bars and a by-weekday chart.
- **Reminders** on scheduled days that aren't done yet.
- **Widgets** (display only): a six-tile grid, and a resizable single-habit widget with the same
  type-specific progress as the app. Larger rounded-square tiles and
  icons replace visible habit names; roomy sizes keep progress/status text. Habit names remain available to TalkBack.
  Choose **Framed** (opaque background with subtle corners) or **Transparent** (tiles directly on
  your wallpaper) for **Grid** or **Single Habit** in the widget picker: four resizable choices.
  Transparent tiles may be harder to see on busy wallpapers.
  Widget progress borders and fills follow the selected highlight color, with fills behind borders.
- **Glyph Toys:** Habit shows a selected habit's icon and progress (display only); Timers has countdowns,
  long press to start or pause, and hold for 2 s to switch timer. Enable either or both.
- **Glyph Progress:** running sessions also publish an Android 16 Live Update so Nothing OS can display
  progress while locked, without cycling to the Timers toy. The toy remains the start/pause/selection
  control surface, with its countdown and session ring but no decorative completion animation.

## Install (development)

There is no consumer download or Play Store release yet. To install a development build, you need JDK 17+, the Android SDK with platform 37, and a Nothing Phone (3) running Nothing OS 4.1 / Android 16 connected with USB debugging enabled.

```sh
echo "sdk.dir=$ANDROID_HOME" > local.properties
python3 scripts/install_debug.py
```

The guarded installer designates one checkout per phone and serializes installs across sessions.
It fetches `origin/main` and refuses a checkout missing either current main or the installed commit.
Other sessions should build/test only, then hand their changes to the install checkout. Direct Gradle
install tasks are disabled; do not bypass the guard with `adb install`. To explicitly transfer ownership,
integrate the previous checkout's changes and run `python3 scripts/install_debug.py --claim`.
For a modified installed build, its owner must commit the changes and install a clean build before
ownership can transfer. With multiple devices, pass `--serial SERIAL`.
The installer requires Python 3.9+ and `adb` on PATH, and fails closed if fetching or verification fails.

Settings > About and the home About sheet show the commit, a modified marker for uncommitted changes,
and UTC build time. The APK embeds the same provenance for installation checks. Legacy APKs without
metadata are accepted once to bootstrap this guard; it cannot establish their original source.
The guard prevents stale ancestry and competing checkout installs, not bugs in newer changes or installs
performed outside this workflow.

To run the unit tests as well, use `./gradlew :app:testDebugUnitTest`. After installing, enable toys in
**Settings › Glyph › Glyph Toys › Set up** and pick the Habit toy's habit under **Displayed habit**.
Short press cycles enabled toys using Nothing's system controls. The habit selection survives app restarts;
if that habit is deleted, pick another (the toy never silently substitutes one). Missing step readings
show "NO STEP DATA" one word at a time. Allow step counting under **Settings › Steps**.

For persistent timer progress, allow Dot Habits notifications and Live Updates, then enable
**Dot Habits** in the phone's **Settings › Glyph › Glyph Progress**. The app's **Settings › Glyph Progress**
section links to notification settings. Starting a timed session from either the app or Timers toy
publishes progress; pausing removes it, and resuming starts a fresh Live Update with retained session time.
Dismissed progress stays hidden for that run. Completion still uses the separate session-finished
notification for sound/vibration, subject to your channel, volume and Do Not Disturb settings.

Background sync writes bounded, non-personal diagnostics for the latest 48 hours. On a development build:

```sh
adb logcat -s DotHabitsWorker DotHabitsRefresh DotHabitsSteps DotHabitsWidget
adb shell run-as com.madebygps.dothabits cat files/background-sync.log
```

### System Glyph Progress

Timer notifications use Android 16
[ProgressStyle / Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update).
Nothing OS renders the Glyph Progress display; Dot Habits supplies session progress and a countdown
deadline, not animation frames. On Phone (3), Nothing OS 4.1 (`B4.1-260814-1733`), the user observed
pulsing between progress increments while logged numeric progress only increased. Nothing's public
SDK does not expose the system renderer or an animation toggle, so the internal effect is unverified.

## Good to know

- **Navigation:** use Android's back gesture or system back button. In-app screen changes use a short fade without shrinking.
- **Typography:** Settings and Statistics use dot-matrix titles; habit names and controls use regular text.
  Dot lettering follows system font sizing, exposes its text to TalkBack, and falls back to regular text when
  characters are unsupported or the scaled dots would not fit.
- **Steps** come from Health Connect's built-in phone step counter, so no tracker app is needed. Only the phone's own
  steps count; other apps writing steps are ignored to avoid double counting. Counting starts once access is granted,
  so there is no earlier history. Steps sync about every 15 minutes in the background and every minute while the app is open.
- **Timers** keep running when the app is closed or the screen is locked, and split at midnight. After a reboot, a
  timer that was running asks you to review its time instead of guessing.
  Session lengths range from 1 to 300 minutes in one-minute steps; history corrections use completed-session counts.
  A running session keeps its original deadline when you edit its goal or history, or when the day changes.
  Switching a habit away from Timer pauses its run. While visible, the app confirms running time every second
  so restart recovery retains the latest observed time; time while asleep remains conservative.
  Short pause/resume runs accumulate before rounding to whole seconds.
- **Live timer progress** refreshes at most every 15 seconds while the process is alive, without a
  foreground service, wake lock or polling alarm. Android ticks the countdown itself; progress-bar updates
  can pause during sleep or process eviction. The ongoing notification expires at session end even if
  the app is not executing. Completion alerts still depend on the session-end alarm and may be delayed
  without exact-alarm permission. Nothing OS controls display priority relative to Glyph Toys and AOD.
- **Glyph Matrix SDK:** Nothing's official [kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
  ships only as an AAR, so it's committed in `app/libs/` with its [EULA](app/libs/GLYPH_SDK_LICENSE.md).
  Commercial use needs Nothing's written permission. No API key is needed on Android 16.

## Verified on device

- Timers Glyph Toy follows the system Glyph brightness
- Both toys appear separately in Nothing's toy manager
- Habit selection saves from the in-app picker and survives an app restart
- The single-habit widget on the home screen
- Timer pause/resume, session limits, notification Pause, background completion after process death, and restart review
- One-minute timer controls and live goal/history edits retaining the running session's deadline
- Debug Live Update recognized by system Glyph Progress; user confirmed locked-screen display,
  temporary Clock override via button, and automatic return to the AOD clock at probe completion
- Production timer Live Update promoted by the OS; user confirmed toy start, locked-screen progress,
  pause/resume, completion alert and progress removal

## Not yet verified on device

- Habit toy's physical LED output
- Glyph Toys on AOD
- Battery use over a full day
- Live timer progress reliability over longer background sessions and process eviction

## License

MIT, see [LICENSE](LICENSE). The Glyph Matrix SDK in `app/libs/` is Nothing's, under its own EULA.

Contributors and agents: see [AGENTS.md](AGENTS.md).
