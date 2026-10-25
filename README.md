# YasinCast

YasinCast is a Persian-first native Android podcast and audio library app. The first milestone is an offline-first local audio player.

## Project documentation

- [Architecture](docs/architecture.md)
- [Phased roadmap](docs/roadmap.md)
- [Playback implementation and verification gaps](docs/player.md)
- [Local audio library and SAF import](docs/library.md)
- [Playback progress and listening history](docs/history.md)
- [Physical-device acceptance checklist](docs/device-acceptance.md)
- [Issue #1 — architecture](https://github.com/yusi20006-max/YasinCast/issues/1)
- [Issue #2 — Android build foundation](https://github.com/yusi20006-max/YasinCast/issues/2)

## Android project

- Application ID: `com.yasin.cast`
- Initial version: `0.1.0`
- Minimum SDK: Android 8.0 (API 26)
- Compile/target SDK: API 36
- JDK: 17
- Android Gradle Plugin: 8.13.0
- Kotlin: 2.2.20
- Gradle distribution: 8.13
- UI: Jetpack Compose + Material 3

These versions are pinned in the project files. They have not yet been verified on the owner's physical phone or local Termux environment.

## Build

Install JDK 17, Android SDK platform/build-tools for API 36, and Gradle 8.13. Then run from the repository root:

```sh
gradle --no-daemon assembleDebug testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds the debug APK and runs unit tests on pushes and pull requests. It uploads the APK as a 14-day artifact.

**Wrapper limitation:** `gradle/wrapper/gradle-wrapper.properties` pins Gradle 8.13, but the official wrapper launcher scripts and JAR are not yet committed. The repository-writing connector supports UTF-8 text files only, so the wrapper JAR cannot be safely added through this route. Until the official wrapper is generated and committed, use an installed Gradle 8.13 as shown above; CI also installs that pinned version.

## Current status

The app includes a Media3 playback service and a Persian RTL interface with library, now-playing, and settings tabs. Users can import multiple audio documents or select a folder through Android's Storage Access Framework, browse saved entries, resume or restart playback, and remove an entry without deleting its source. The player exposes a seek slider, ±15-second controls, playback speed, elapsed/remaining time, and recent history. The URI index is stored locally and persistable read grants are attempted where supported. See [library notes](docs/library.md), [playback notes](docs/player.md), [history notes](docs/history.md), and [UI notes](docs/ui.md). Physical-device behavior and visual layout still require verification on the target phone.
