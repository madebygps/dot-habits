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
- **Glyph Toy** for timer habits: long press starts or pauses, hold for 2 s to switch habit.

## Install (development)

There is no consumer download or Play Store release yet. To install a development build, you need JDK 17+, the Android SDK with platform 37, and a Nothing Phone (3) running Nothing OS 4.1 / Android 16 connected with USB debugging enabled.

```sh
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:installDebug
```

To run the unit tests as well, use `./gradlew :app:testDebugUnitTest`. After installing, add the toy in **Settings › Glyph Toy › Set up**, and allow step counting under **Settings › Steps**.

## Good to know

- **Navigation:** use Android's back gesture or system back button. In-app screen changes use a short fade without shrinking.
- **Steps** come from Health Connect's built-in phone step counter, so no tracker app is needed. Only the phone's own
  steps count; other apps writing steps are ignored to avoid double counting. Counting starts once access is granted,
  so there is no earlier history. Steps sync about every 15 minutes in the background and every minute while the app is open.
- **Timers** keep running when the app is closed or the screen is locked, and split at midnight. After a reboot, a
  timer that was running asks you to review its time instead of guessing.
- **Glyph Matrix SDK:** Nothing's official [kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
  ships only as an AAR, so it's committed in `app/libs/` with its [EULA](app/libs/GLYPH_SDK_LICENSE.md).
  Commercial use needs Nothing's written permission. No API key is needed on Android 16.

## Verified on device

- Glyph Toy follows the system Glyph brightness
- The single-habit widget on the home screen

## Not yet verified on device

- Glyph Toy on AOD
- Battery use over a full day
- Reboot with a timer running

## License

MIT, see [LICENSE](LICENSE). The Glyph Matrix SDK in `app/libs/` is Nothing's, under its own EULA.

Contributors and agents: see [AGENTS.md](AGENTS.md).
