# Contributor instructions

See [README](README.md) for setup and build commands.

## Product guidance

`app/src/main/java/com/madebygps/dothabits/domain/HabitGuide.kt` owns product guidance shared by
the app and generated [product guide](docs/product.md). Read it before changing behaviour; verify
claims against implementation and tests. Do not edit the generated guide directly.

If product functionality or goals change, update `HabitGuide.kt` and regenerate `docs/product.md`.
Include both changes so the app and published guide stay consistent:

```sh
./gradlew :app:testDebugUnitTest --tests '*ProductGuideTest' -PupdateProductGuide
```

## Implementation

- Keep behaviour and presentation rules in pure Kotlin `domain/`, with tests in `app/src/test`.
  App, widgets and Glyph use the same `TodaySnapshot`.
- `HabitRepository` is the only data writer; notify through `onDataChanged` after writes.
  `TimerLifecycle` owns timer expiry and alarm reconciliation. App and Glyph share transactional
  transitions; reject stale session/generation callbacks.
- Use documented platform/SDK APIs only. Use AndroidX Live Update compatibility APIs: Phone (3)
  may run API 36.0, below the platform promotion API's 36.1 requirement.
- No internet permission, backend, accounts, analytics, ads or fabricated history/step data.
  Filter Health Connect steps to origin `android` plus the device's synthetic package name.
- No foreground service, wake lock or polling alarms. Steps refresh every 15 minutes in the
  background and every minute while resumed; live timer progress at most every 15 seconds.
  Alarms are only for reminders, midnight and session end. Use `setWindow` without optional
  `SCHEDULE_EXACT_ALARM` permission; never request `USE_EXACT_ALARM`.
- AGP uses built-in Kotlin; do not add `kotlin-android`. Keep the bundled Glyph SDK and its EULA
  together. Commercial use requires Nothing's written permission.

## Device and data safety

- Install only on the intended Nothing Phone (3). Report build/device failures; never claim
  on-device verification without doing it.
- Support only the current Room schema in `app/schemas/`. No legacy migrations, destructive
  fallbacks or clearing user data to resolve version mismatches.
- Before a schema change, stop the app and privately back up its database, WAL and SHM. Bump the
  schema version; perform any one-off conversion on a separate local copy and validate integrity
  and data preservation before restoring. Keep the original backup unchanged and private data
  out of the repository.
