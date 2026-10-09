# AGENTS.md — working on Dot Habits

Dot Habits is a native Android habit tracker built **only** for Nothing Phone (3) on
Nothing OS 4.x / Android 16. Read "Product rules" below before changing behaviour.

## Build

```sh
export JAVA_HOME=/path/to/jdk-17          # AGP 9 needs JDK 17+
./gradlew :app:testDebugUnitTest          # pure-JVM domain tests (fast)
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:installDebug               # install the updated build on the connected phone
```

- After every change, install the updated debug build on the connected Nothing Phone (3). If no phone is available, report that the install is blocked.
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
- Timer progress reflects the current session, not the whole daily goal. Cancel on pause, completion or review;
  set notification timeout to session end, respect dismissal for that run, and keep the separate completion alert.
  Refresh progress at most every 15 seconds while running and the process is alive; no foreground service,
  wake lock or additional polling alarms. Android owns the ticking chronometer; progress-bar refresh is best effort
  when asleep or after process death. Never promise uninterrupted background animation.
- Use the stored run limit and monotonic anchor for Live Update expiry/countdown; do not derive its deadline
  anew from rounded daily totals. Duration/history edits and midnight must not wrap a still-running progress bar.
  Nothing OS owns Glyph Progress's visual effects: numeric progress increasing is not proof of a static
  LED animation, and the SDK does not expose a system-progress animation toggle.
- Timed habits are sessions × minutes; each run carries `limitSeconds` and stops itself at the session end.
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
- Timers: wall-clock `start`/`end` + monotonic anchor, so no foreground service. Split at midnight. After a reboot
  (`BOOT_COUNT` changed) a running session needs review and counts only to its last confirmed-alive time.
- Alarms only for reminders, midnight rollover and session end; use `setWindow` (Phone (3) gave
  `setAndAllowWhileIdle` a 1-hour window). `SCHEDULE_EXACT_ALARM` optional, never `USE_EXACT_ALARM`.
- Stats count closed opportunities only (scheduled past days, or finished weeks); today and the current week never count.

## Architecture

- `domain/` — pure Kotlin rules (schedules, streaks, timers, snapshot). All logic that decides
  what a surface shows lives here and is unit-tested. Streaks are always recomputed from raw history.
- `data/` — Room (habits, entries, timer sessions, cached daily steps), DataStore settings,
  Health Connect reads. `HabitRepository` is the only writer and calls `onDataChanged` after writes.
- `system/` — notifications, AlarmManager (reminders, midnight, timer session end), receivers, WorkManager.
  `TimerNotificationUpdater` serializes fresh snapshots for Live Updates and updates only while runs exist.
- `widget/` and `glyph/` render from the same `TodaySnapshot` the app uses.
- `ui/` — custom Compose UI (black, dot-matrix accents).

Room schema JSON in `app/schemas/` is committed; bump the DB version and add a migration for schema changes.
Add or update unit tests in `app/src/test` whenever domain rules change.
