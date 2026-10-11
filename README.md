# Dot Habits

Native Android habit tracker for Nothing Phone (3).

## Open the project

1. Clone this repository and open its root folder in Android Studio.
2. Install Android SDK platform 37 and select JDK 17+ for Gradle.
3. Let Gradle sync. Android Studio creates `local.properties` with your SDK path.

For command-line builds, set `ANDROID_HOME` to your SDK directory and `JAVA_HOME` to your JDK.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

To install, connect the intended Nothing Phone (3) running Nothing OS 4.x / Android 16 with USB
debugging enabled:

```sh
./gradlew :app:installDebug
```

With multiple devices, use `ANDROID_SERIAL=SERIAL ./gradlew :app:installDebug`.
Read [AGENTS.md](AGENTS.md) before updating an existing installation or changing its
database schema. There is no consumer download or Play Store release.

## Live-progress prototype

A `specialUse` foreground service keeps a running timer's Live Update out of Android's
cached-process freezer. It stops with pause, completion, discard or notification dismissal;
timer accounting and completion alarms remain independent. No wake lock or polling alarm is used.

The connected Phone (3) rejected service starts from the locked Glyph toy while battery-optimized.
After manually granting the battery-optimization exemption, toy-only starts succeeded and
notification progress continued beyond the toy timeout with the app closed and phone locked.
An isolated test with the exemption but no foreground service still entered the cached-process
freezer and stopped progress updates after the toy timed out.
Without that exemption, opening the app retries the start. Unplugged deep-sleep behavior is not
verified. The prototype does not promise uninterrupted bars.

Settings > Glyph Progress shows the actual exemption status and an Open battery settings button.
Managing live progress shows a one-time explanation if notifications and Live Updates are allowed
but the exemption is missing. Android requires the user's approval; the app does not grant access.

[Product guide](docs/product.md) (also in Settings > How Dot Habits works) ·
[Contributor instructions](AGENTS.md) · [MIT licence](LICENSE) ·
[Nothing Glyph SDK EULA](app/libs/GLYPH_SDK_LICENSE.md)
