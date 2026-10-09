# Dot Habits

A habit tracker for **Nothing Phone (3)** (Nothing OS 4.1 / Android 16), inspired by [Streaks](https://streaksapp.com/).
Black UI, dot-matrix icons, one highlight colour. Works fully offline, with no account and no `INTERNET` permission.

## Features

- **Home:** six rings per page (2×3), up to 24 habits. Hold a ring to log, tap it for details. Completed rings fill in.
- **About:** tap the centered app icon for the installed version, creator credit, and GitHub repo.
- **Habit types:** check/count (e.g. meds 2×/day), timer (sessions × minutes, e.g. Deep Work 4 × 25), steps, and avoid.
- **Schedules:** every day, selected weekdays, N distinct days/week, or N times/week. The week starts Monday by default.
- **Streaks:** daily streaks skip unscheduled days; weekly streaks reset only when a week closes below its goal.
- **History:** edit or backfill any day; streaks and stats recalculate.
- **Statistics:** 30/60/90-day or all-time completion rate, weekly bars and a by-weekday chart.
- **Reminders** on scheduled days that aren't done yet.
- **Widgets** (display only): a six-ring grid, and a resizable single-habit widget.
- **Glyph Toys:** Habit shows a selected habit's icon and progress (display only); Timers has countdowns,
  long press to start or pause, and hold for 2 s to switch timer. Enable either or both.

## Install (development)

There is no consumer download or Play Store release yet. To install a development build, you need JDK 17+, the Android SDK with platform 37, and a Nothing Phone (3) running Nothing OS 4.1 / Android 16 connected with USB debugging enabled.

```sh
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:installDebug
```

To run the unit tests as well, use `./gradlew :app:testDebugUnitTest`. After installing, enable toys in
**Settings › Glyph › Glyph Toys › Set up** and pick the Habit toy's habit under **Displayed habit**.
Short press cycles enabled toys using Nothing's system controls. The habit selection survives app restarts;
if that habit is deleted, pick another (the toy never silently substitutes one). Missing step readings
show "NO STEP DATA" one word at a time. Allow step counting under **Settings › Steps**.

Background sync writes bounded, non-personal diagnostics for the latest 48 hours. On a development build:

```sh
adb logcat -s DotHabitsWorker DotHabitsRefresh DotHabitsSteps DotHabitsWidget
adb shell run-as com.madebygps.dothabits cat files/background-sync.log
```

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
- **Glyph Matrix SDK:** Nothing's official [kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
  ships only as an AAR, so it's committed in `app/libs/` with its [EULA](app/libs/GLYPH_SDK_LICENSE.md).
  Commercial use needs Nothing's written permission. No API key is needed on Android 16.

## Verified on device

- Timers Glyph Toy follows the system Glyph brightness
- Both toys appear separately in Nothing's toy manager
- Habit selection saves from the in-app picker and survives an app restart
- The single-habit widget on the home screen

## Not yet verified on device

- Habit toy's physical LED output
- Glyph Toys on AOD
- Battery use over a full day
- Reboot with a timer running

## License

MIT, see [LICENSE](LICENSE). The Glyph Matrix SDK in `app/libs/` is Nothing's, under its own EULA.

Contributors and agents: see [AGENTS.md](AGENTS.md).
