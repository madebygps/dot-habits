# Dot Habits

A native habit tracker for **Nothing Phone (3)** (Nothing OS 4.1 / Android 16), with
display-only home-screen widgets and a Glyph Matrix Toy. The workflow follows
[Streaks](https://streaksapp.com/). The design, icons and branding are original: black UI,
monochrome dot-matrix icons and one highlight colour.

Everything stays on the phone. There's no account, no server, no `INTERNET` permission and no analytics.

> **Status:** the code compiles and the JVM unit tests pass. It has **not been run on a Nothing Phone (3)**.
> See [Needs real-device verification](#needs-real-device-verification).

## Features

- **Home:** a 2-column × 3-row grid of large progress rings, six habits per page, up to 24 habits on four pages.
  Each ring has a monochrome dot icon in the middle and the label underneath. Page dots, Statistics and Settings sit at the bottom.
- **Press and hold** a ring (about 0.5 s, with a haptic tick) to log a completion. An **Undo** snackbar follows.
  A tap opens the habit's details.
- **Habit types**
  - *Check / count:* one or more completions a day. For example, Chonk Meds 2×/day shows a ring split into 2 segments.
  - *Timer:* sessions add up toward a daily duration, for example Read 1 hour. A play/pause button sits on the ring's lower-right.
  - *Steps:* read automatically from Health Connect.
  - *Avoid:* negative habits. Holding logs a slip, and the day succeeds while slips stay within the allowance.
- **Schedules:** every day, selected weekdays, N *distinct* days per week, or N times per week.
- **Streaks:** current and best. Daily streaks follow the schedule. Weekly streaks count consecutive successful weeks.
- **Details:** a big ring, streak stats, days met, 30-day rate and a month calendar.
  Tap any past day to **edit or backfill** amounts and add a **daily note**. Timer sessions are listed and can be deleted.
- **Reminders:** per-habit times. They only fire on scheduled days, and only when the habit isn't already done.
- **Settings:** highlight colour (Signal red by default, shared by the app and widgets), week start (Monday by default),
  Health Connect, Glyph and permission status, and habit order.
- **Widget:** display-only, with three responsive sizes. Tapping anywhere opens the app.
- **Glyph Toy:** shows today's progress, or the active timer. **Long press** switches between the two views.

## Setup

Requirements:

- JDK 17+. AGP 9 runs on 17; the build was tested with Homebrew `openjdk@17`.
- Android SDK with **platform 37** and build-tools 36+. The app targets API 36 (Android 16), but current
  AndroidX (Compose 1.12, Navigation 2.10) must compile against API 37.
- Gradle comes from the wrapper (9.8.0).

```sh
echo "sdk.dir=$ANDROID_HOME" > local.properties
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:testDebugUnitTest      # domain unit tests
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Glyph Matrix SDK

The Glyph Toy uses Nothing's official
[GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).
The SDK is only published as an AAR (there's no Maven artifact), so it's committed at
`app/libs/glyph-matrix-sdk-2.0.aar`, the way Nothing's own README and its official
[example project](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Example-Project) do it.
Bundling it into the APK is how every Glyph Toy works; there's no system-provided copy of the SDK.

Licence: the SDK has its own [EULA](app/libs/GLYPH_SDK_LICENSE.md), separate from this repo's code.
It allows use "for the purpose of integrating and using the Software's functionality within your applications",
but **commercial use needs prior written permission from Nothing** (§2.2, contact GDKsupport@nothing.tech).
That's fine for this free personal project. Get permission before charging for the app or adding ads or in-app purchases.

The manifest declares `com.nothing.ketchum.permission.ENABLE` and `<meta-data android:name="NothingKey" android:value="test"/>`.
Nothing's kit docs say apps targeting Android 16 no longer need a real API key, and the `test` value is kept as they recommend.
There's no secret in the repo.

## Product rules

These live in `domain/` and are covered by the tests in `app/src/test`.

**Targets and values.** Values are stored per day, in counts for check/count habits, seconds for timers and steps for steps.
The app never invents a value.

| Schedule | Success | Streak unit |
|---|---|---|
| Every day | the daily target is met | days |
| Selected weekdays | the daily target is met on each scheduled day; other days are *rest days* | days (rest days are skipped and never break a streak) |
| N days / week | the daily target is met on N **distinct** days. Four completions on one day = 1 day (the Workout example) | weeks |
| N times / week | N completions in total, on any days | weeks |

- **Daily streaks** count consecutive *scheduled* days that met the goal. An unmet today is *pending*, not broken.
- **Weekly streaks** count consecutive successful weeks. The current week stays pending until it closes, so a streak
  only resets once a week ends below its goal. The week a habit was created in is a grace week.
  The week start is configurable (Monday by default), and changing it regroups the history.
- **Avoid habits** (count only, daily or weekday schedule): a day succeeds when slips ≤ the allowance.
  Today counts only once it's over, but going over the allowance breaks the streak straight away.
- **Editing history:** any past day can be edited, backfilled or cleared, including days before the habit was created.
  Streaks, rates, widgets and the Glyph are all recomputed from raw history, so the numbers always match.
- **Hold behaviour:** a hold logs +1 until the daily target is reached. On N-days/week habits it logs at most the
  daily target per day. On a timer it starts or pauses the timer. On steps it does nothing, because steps are automatic.
  More can be backfilled from the detail screen.

### Timers, date boundaries and reboots

- A session is stored as wall-clock instants (`start`, `end`), plus the monotonic `elapsedRealtime` at start. Nothing depends on the process staying alive, so
  closing the app or locking the screen loses nothing. Only one timer runs at a time; starting another pauses the first.
- While a timer runs there's an ongoing notification with a system chronometer and a Pause action.
  An exact alarm fires when the daily goal is reached. **No foreground service is used:** the time is computed from
  timestamps, so a service would only cost battery. None of the Android 14+ foreground-service types fit a long
  habit timer either.
- **Midnight:** a session that crosses midnight is split, and each day gets the time that actually fell on it.
  An alarm at 00:00:02 rolls the widget and Glyph over to the new day. It is exact if "Alarms & reminders" is allowed, otherwise
  within a 10-minute window (Android's minimum for inexact alarms).
  On the Phone (3), `setAndAllowWhileIdle` got a 1-hour window, so the app uses `setWindow` instead.
- **Reboot:** each session records `Settings.Global.BOOT_COUNT`. After a reboot, a session that was running is marked
  **Needs review**, and it counts *only up to the last moment the app confirmed it was running*. The app confirms this
  on app open, on Glyph toy ticks and on the hourly worker. A notification and a card on the detail screen let you
  keep the time up to that last confirmation, keep it up to the restart time, or discard the session.
  Time is never silently lost and never over-counted.
- **Clock changes:** while a session is running, its length is measured on the monotonic clock, so moving the clock by hand
  or a network time correction neither adds nor removes time. On `TIME_SET` the stored start is re-anchored to the new
  wall clock. The wall clock only decides which calendar day the time counts toward. Closed sessions keep their recorded times.

### Reminders and background behaviour

- AlarmManager is used only for things the user would notice if they were late: the next reminder, midnight rollover
  and the timer-goal moment. One reminder alarm is scheduled at a time and is recomputed after every change, after boot, and after time or zone changes.
- Exact alarms (`SCHEDULE_EXACT_ALARM`) are optional. Android 14+ denies them by default. Without them, reminders use a
  10-minute window, and Settings shows the status with a button to allow them. `USE_EXACT_ALARM` is deliberately not requested.
- WorkManager runs hourly (deferrable, only when the battery isn't low) to sync steps and refresh widgets. Widgets otherwise update
  only when data changes (`updatePeriodMillis = 0`).
- The Glyph Toy reads new data once a minute and pushes a frame only when the pixels change. While the screen is visible, the app UI ticks every second.

## Steps and Health Connect (phone only)

**Findings**, from the official docs at
[developer.android.com/health-and-fitness/health-connect/features/steps](https://developer.android.com/health-and-fitness/health-connect/features/steps):

- On **Android 14+ with SDK extension level ≥ 20**, Health Connect counts steps *itself* from the phone's
  low-power step counter. No Fitbit, Google Fit or other tracker app is needed. So the older claim that Health Connect never counts steps is out of date.
- Counting starts only after **some app is granted `READ_STEPS`**. There's **no history from before that grant**.
- Those steps are attributed to `android`, or to a synthetic per-device package from mid-2026. Dot Habits aggregates
  daily totals **without a data-origin filter**, so they're always included. Steps from any other source are included too, and Health Connect de-duplicates them.

**What Dot Habits does**

- It checks `HealthConnectClient.getSdkStatus` and `SdkExtensions.getExtensionVersion(UPSIDE_DOWN_CAKE)`,
  and Settings shows the actual state:
  - Health Connect is unavailable or needs an update (with a link to the store).
  - The extension level is below 20, so no on-device counting and another source is needed.
  - Steps aren't allowed yet.
  - Counting is on, and whether background refresh is on.
- It requests `READ_STEPS`, plus `READ_HEALTH_DATA_IN_BACKGROUND` when that feature is available, so the widget and Glyph update hourly.
- When there's no reading for today, the habit shows **"NO STEP DATA"**. There's no estimate and no silent fallback.
- The privacy/rationale screen (`PrivacyActivity`) is wired to Health Connect's rationale and permission-usage intents.

**Unknown:** the SDK extension level that Nothing OS 4.1 reports on the Phone (3) couldn't be checked without the device.
If it's below 20, the app says so. Then the only supported alternative is an app that writes steps to Health Connect.
Without a source like that, step habits can't fill in automatically. No third-party tracker is required unless that check fails.

## Glyph Matrix Toy (Phone (3), `Glyph.DEVICE_23112`, 25×25)

- It's a standard toy service: the `com.nothing.glyph.TOY` intent filter, name, preview, summary, `longpress=1` and `aod_support=1`.
  The system binds and unbinds it. The user opts in by adding *Dot Habits* in the Glyph Toys manager.
  Settings › Glyph opens that manager when the documented intent resolves (system builds 20250829 and later). Otherwise it points you to system Settings.
- **Today view:** a ring for done/due today, with `n/m` text, or a check mark when everything's done.
  **Timer view:** a ring for progress toward the goal, a play or pause mark, and minutes (or h:mm).
- **Long press** (`EVENT_CHANGE`) only switches the view, and the choice is saved. Short press cycles toys and belongs to the system.
  The toy never completes habits and never controls timers.
- **AOD:** it answers `EVENT_AOD` (sent once a minute when it's chosen as the AOD toy) with a fresh frame.
- Frames are built as a bitmap → `GlyphMatrixObject` with the documented 0–255 brightness, which is adjustable in Settings.
  It's always monochrome. The app does **not** use app-level `setAppMatrixFrame`, so there are no unsolicited Glyph reminders and no always-on control beyond what the toy framework grants.

## Widget sizes

Glance responsive sizes: **small** (≥110×110 dp, about 2×2) shows the overall ring with done/due.
**Wide** (≥250×110 dp, about 4×2) shows the first six rings in a row.
**Large** (≥250×230 dp, about 4×4) shows six rings with labels.
They use the same highlight colour and the same `TodaySnapshot` as the app. The widget is display-only, and a tap opens the app.

## Needs real-device verification

None of these have been tested on hardware:

1. **Health Connect:** the SDK extension level and on-device step counting on Nothing OS 4.1, the background-read feature, and the permission flow.
2. **Glyph Toy:** that it registers in the Toys manager, the frame orientation and brightness on the 25×25 matrix, long press, AOD ticks,
   `register(DEVICE_23112)`, the `Build.MODEL` check (`A024`), and the Toys manager intent.
3. **Widgets:** the real cell sizes on Nothing Launcher and the responsive breakpoints.
4. **Layout:** the 2×3 ring grid on the Phone (3) display, including edge-to-edge insets and gesture navigation.
5. **Alarms and notifications:** exact versus windowed reminders under Nothing OS battery management, the midnight rollover, and the timer goal alarm.
6. **Reboot flow:** a running timer → reboot → the Needs-review card and notification.
7. **Battery:** real-world drain from the hourly worker and Glyph AOD.

## Project layout

```
app/src/main/java/com/madebygps/dothabits/
  domain/   pure rules: Model, HabitRules (schedules/streaks), TimerMath, Snapshot, CompletionPolicy, ReminderPlanner, DotArt
  data/     Room database, DataStore settings, HabitRepository (single writer), StepsRepository (Health Connect)
  system/   Notifications, Alarms, receivers, Refresh fan-out, StepsSyncWorker
  widget/   Glance widget (display-only)
  glyph/    GlyphToyService, GlyphFrames (pure 25×25 renderer), GlyphSupport
  ui/       Compose screens: Home, Detail, Edit, Stats, Settings, Privacy
app/libs/           Nothing GlyphMatrix SDK AAR + its licence
app/src/test/       JVM unit tests
```

See `AGENTS.md` for contributor and agent constraints.
