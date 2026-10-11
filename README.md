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

[Product guide](docs/product.md) (also in Settings > How Dot Habits works) ·
[Contributor instructions](AGENTS.md) · [MIT licence](LICENSE) ·
[Nothing Glyph SDK EULA](app/libs/GLYPH_SDK_LICENSE.md)
