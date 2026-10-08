# AGENTS.md — working on Dot Habits

Dot Habits is a native Android habit tracker built **only** for Nothing Phone (3) on
Nothing OS 4.x / Android 16. Read `README.md` → "Product rules" before changing behaviour.

## Build

```sh
export JAVA_HOME=/path/to/jdk-17          # AGP 9 needs JDK 17+
./gradlew :app:testDebugUnitTest          # pure-JVM domain tests (fast)
./gradlew :app:assembleDebug :app:lintDebug
```

- `compileSdk 37` (required by current AndroidX), `minSdk = targetSdk = 36`.
- AGP 9 built-in Kotlin: do not add the `kotlin-android` plugin.
- The Nothing GlyphMatrix SDK is committed at `app/libs/glyph-matrix-sdk-2.0.aar` (no Maven artifact
  exists), with its EULA alongside. Keep the licence file; update both together.

## Hard constraints

- Never commit keys or secrets. Glyph SDK EULA §2.2: no commercial use (paid app, ads, IAP) without written permission from Nothing.
- Local-first: no `INTERNET` permission, backend, account, analytics or ads.
- Widgets are **display-only**: the whole widget opens the app; no completion controls.
- The Glyph Toy is timers only. Long-press (`EVENT_CHANGE`) starts/pauses the shown timer (user's explicit
  choice); it must never log completions directly. Short press is system toy cycling — don't intercept it.
  Holding ≥2 s (timed between documented `action_down`/`action_up`) switches timers; decide on release so a hold never also toggles.
- Timed habits are sessions × minutes; each run carries `limitSeconds` and stops itself at the session end.
- Glyph output stays monochrome; the highlight colour applies to app + widgets only.
- No Essential Space integration and no Essential Key remapping.
- Never fabricate data: no seeded history, no estimated steps. Missing step data shows "NO STEP DATA".
- Steps come only from the phone's own counter: filter Health Connect reads to origin `android` plus the
  device's synthetic package name. Never use the unfiltered merged total; other writers double count.
- Step sync: WorkManager every 15 min in the background, every minute while the app is resumed. Don't add faster polling.
- Only use documented SDK/platform APIs; verify against official docs before adding new ones.
- Don't claim on-device verification that wasn't done.

## Architecture

- `domain/` — pure Kotlin rules (schedules, streaks, timers, snapshot). All logic that decides
  what a surface shows lives here and is unit-tested. Streaks are always recomputed from raw history.
- `data/` — Room (habits, entries, timer sessions, cached daily steps), DataStore settings,
  Health Connect reads. `HabitRepository` is the only writer and calls `onDataChanged` after writes.
- `system/` — notifications, AlarmManager (reminders, midnight, timer session end), receivers, WorkManager.
- `widget/` and `glyph/` render from the same `TodaySnapshot` the app uses.
- `ui/` — custom Compose UI (black, dot-matrix accents).

Room schema JSON in `app/schemas/` is committed; bump the DB version and add a migration for schema changes.
Add or update unit tests in `app/src/test` whenever domain rules change.
