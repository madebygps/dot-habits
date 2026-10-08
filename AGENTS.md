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
- Glyph SDK is optional at build time: with `app/libs/glyph-matrix-sdk-2.0.aar` present the
  real `src/glyph` service is compiled; otherwise `src/glyphStub`. Keep both building.

## Hard constraints

- **Never commit the Glyph Matrix SDK AAR** (its EULA forbids redistribution) or any key/secret.
- Local-first: no `INTERNET` permission, backend, account, analytics or ads.
- Widgets are **display-only**: the whole widget opens the app; no completion controls.
- Glyph Toy long-press (`EVENT_CHANGE`) **only** switches Today ↔ Timer view. It must never
  complete habits or start/pause timers. Short press is system toy cycling — don't intercept it.
- Glyph output stays monochrome; the highlight colour applies to app + widgets only.
- No Essential Space integration and no Essential Key remapping.
- Never fabricate data: no seeded history, no estimated steps. Missing step data shows "NO STEP DATA".
- Only use documented SDK/platform APIs; verify against official docs before adding new ones.
- Don't claim on-device verification that wasn't done.

## Architecture

- `domain/` — pure Kotlin rules (schedules, streaks, timers, snapshot). All logic that decides
  what a surface shows lives here and is unit-tested. Streaks are always recomputed from raw history.
- `data/` — Room (habits, entries, timer sessions, cached daily steps, notes), DataStore settings,
  Health Connect reads. `HabitRepository` is the only writer and calls `onDataChanged` after writes.
- `system/` — notifications, AlarmManager (reminders, midnight, timer goal), receivers, WorkManager.
- `widget/` and `glyph/` render from the same `TodaySnapshot` the app uses.
- `ui/` — custom Compose UI (black, dot-matrix accents).

Room schema JSON in `app/schemas/` is committed; bump the DB version and add a migration for schema changes.
Add or update unit tests in `app/src/test` whenever domain rules change.
