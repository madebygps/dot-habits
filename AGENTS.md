# AGENTS.md — working on Dot Habits

Dot Habits is a native Android habit tracker built **only** for Nothing Phone (3) on
Nothing OS 4.x / Android 16. Read "Product rules" below before changing behaviour.

## Build

```sh
export JAVA_HOME=/path/to/jdk-17          # AGP 9 needs JDK 17+
./gradlew :app:testDebugUnitTest          # pure-JVM domain tests (fast)
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:installDebug               # build and install on the connected phone
```

- Use `./gradlew :app:installDebug` to build and install. There is no checkout ownership, install lock,
  or Git freshness gate. Coordinate with other sessions before installing and preserve their changes;
  ask before replacing a build whose changes are not included. Report build/device failures.
- Install only on the intended Nothing Phone (3). With multiple devices, select it using
  `ANDROID_SERIAL=SERIAL ./gradlew :app:installDebug`.
- Settings > About and the home About sheet show the Git commit, modified marker and localized build time
  (phone locale, time zone and 12/24-hour preference). APK provenance retains the UTC timestamp.
  Build metadata is informational, not an installation gate.
- `compileSdk 37` (required by current AndroidX), `minSdk = targetSdk = 36`.
- AGP 9 built-in Kotlin: do not add the `kotlin-android` plugin.
- The Nothing GlyphMatrix SDK is committed at `app/libs/glyph-matrix-sdk-2.0.aar` (no Maven artifact
  exists), with its EULA alongside. Keep the licence file; update both together.

## Hard constraints

- Never commit keys or secrets. Glyph SDK EULA §2.2: no commercial use (paid app, ads, IAP) without written permission from Nothing.
- Local-first: no `INTERNET` permission, backend, account, analytics or ads.
- Widgets are **display-only**: the whole widget opens the app; no completion controls.
- The Habit Glyph Toy is display-only: choose its habit in app Settings; never log completions or control timers.
  The Timers Glyph Toy is timers only. Long-press (`EVENT_CHANGE`) starts/pauses the shown timer (user's explicit
  choice); it must never log completions directly. Short press is system toy cycling — don't intercept it.
  Holding ≥2 s (timed between documented `action_down`/`action_up`) switches timers; decide on release so a hold never also toggles.
- Keep the Timers toy's functional countdown, running/paused indicator, session ring and selection feedback;
  no decorative completion animation. Completion sound/vibration belongs to the existing session-finished notification.
- Active timer notifications use AndroidX `ProgressStyle` + `setRequestPromotedOngoing` for Nothing's system
  Glyph Progress. Keep the toy as the button control surface; Glyph Progress is complementary passive monitoring.
  Use AndroidX compatibility APIs: the platform promotion builder method requires API 36.1, while Phone (3)
  builds may run 36.0. No private APIs or vendor-app impersonation.
- Timer progress reflects the current session, not the whole daily goal. Cancel on pause, completion or discard;
  set notification timeout to session end, respect dismissal for that run, and keep the separate completion alert.
  Refresh progress at most every 15 seconds while running and the process is alive; no foreground service,
  wake lock or additional polling alarms. Android owns the ticking chronometer; progress-bar refresh is best effort
  when asleep or after process death. Never promise uninterrupted background animation.
- Use the stored run limit and monotonic anchor for Live Update expiry/countdown; do not derive its deadline
  anew from rounded daily totals. Duration/history edits and midnight must not wrap a still-running progress bar.
  Nothing OS owns Glyph Progress's visual effects: numeric progress increasing is not proof of a static
  LED animation, and the SDK does not expose a system-progress animation toggle.
- Timed habits are sessions × minutes. A logical session captures `limitSeconds`, remaining milliseconds,
  original credit day and a monotonic running anchor. Pause/resume retains that session. Duration, goal and
  history edits preserve it while today's recorded credit is below the current daily goal. Meeting today's
  goal through timer completion, manual correction or goal/duration edits discards unfinished time without
  extra credit and prevents restarting for that day. Correcting below goal allows a fresh timer, never
  restores discarded partial time. Only full planned timer completion writes automatic credit, once.
- Glyph output stays monochrome; the highlight colour applies to app + widgets only.
- No Essential Space integration and no Essential Key remapping.
- Never fabricate data: no seeded history, no estimated steps. Missing step data shows "NO STEP DATA".
- Steps come only from the phone's own counter: filter Health Connect reads to origin `android` plus the
  device's synthetic package name. Never use the unfiltered merged total; other writers double count.
- Step sync: WorkManager every 15 min in the background, every minute while the app is resumed. Don't add faster polling.
- Only use documented SDK/platform APIs; verify against official docs before adding new ones.
- Don't claim on-device verification that wasn't done.

## Product rules

- Values are stored per day: counts, timer seconds, or steps. N days/week counts **distinct** days (four logs on one day = 1 day).
- Daily streaks count consecutive scheduled days; rest days never break them and an unmet today is pending.
  Weekly streaks reset only when a week closes below goal; the creation week is a grace week.
- Avoid habits succeed while slips ≤ allowance; going over breaks the streak immediately.
- Hold logs +1 up to the daily target; on timers it starts/pauses; on steps it does nothing.
- Timers: wall-clock anchor + monotonic elapsed time, so no foreground service. Unfinished running and
  paused sessions are discarded at midnight or after reboot (`BOOT_COUNT` changed); no split, carry,
  partial credit, heartbeat checkpoint or recovery review. A delayed completion before midnight retains
  its original day's credit. One timer runs at a time; starting another pauses the previous session.
- Manual timer history edits replace the day's completed credit in seconds; no signed offsets against
  active time. Current habit duration evaluates historical seconds. Past-day corrections do not affect
  today's timer; meeting today's current daily goal discards it. Weekly goals met on other days do not
  discard today's incomplete timer.
- Alarms only for reminders, midnight rollover and session end; use `setWindow` (Phone (3) gave
  `setAndAllowWhileIdle` a 1-hour window). `SCHEDULE_EXACT_ALARM` optional, never `USE_EXACT_ALARM`.
- Stats count closed opportunities only (scheduled past days, or finished weeks); today and the current week never count.

## Architecture

- `domain/` — pure Kotlin rules (schedules, streaks, timers, snapshot). All logic that decides
  what a surface shows lives here and is unit-tested. Streaks are always recomputed from raw history.
- `data/` — Room (habits, entries, timer sessions, cached daily steps), DataStore settings,
  Health Connect reads. `HabitRepository` is the only writer and calls `onDataChanged` after writes.
- `system/` — notifications, AlarmManager (reminders, midnight, timer session end), receivers, WorkManager.
  `TimerLifecycle` owns process-local deadline/midnight expiry and OS alarm reconciliation. App and Glyph
  commands share transactional repository transitions; stale session/generation callbacks are ignored.
  `TimerNotificationUpdater` only displays fresh snapshots, at most every 15 seconds while runs exist.
- `widget/` and `glyph/` render from the same `TodaySnapshot` the app uses.
- `ui/` — custom Compose UI (black, dot-matrix accents).

Only the current Room schema (v5) in `app/schemas/` is committed and supported. This is a sole-user
installation: there are no legacy migrations or destructive fallbacks. Unsupported database versions
must fail safely, never reset user data. Before a schema change, stop the app and privately back up
the installed database with its WAL/SHM files. Bump the version for structural changes and perform
any required one-off conversion on a separate local copy, validating integrity and data preservation
before restoring it. Keep the original backup unchanged; do not retain historical compatibility code
or private data in the repository.
Add or update unit tests in `app/src/test` whenever domain rules change.
