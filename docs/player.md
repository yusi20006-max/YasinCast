# Playback implementation (Issue #3)

## Current implementation

- `PlaybackService` owns an AndroidX Media3 ExoPlayer and a MediaSession. The player is not owned by the Compose screen lifecycle.
- The service declares the Media3 `MediaSessionService` action and Android's `mediaPlayback` foreground-service type.
- Media3 is responsible for exposing session transport state to compatible system UI, including its media notification and lock-screen controls.
- Audio focus is requested through ExoPlayer audio attributes; `setHandleAudioBecomingNoisy(true)` pauses playback when the audio output becomes noisy (for example, wired headset disconnect).
- The player configures 15-second seek-back/seek-forward increments and supports playback speed from 0.8x through 2.0x via the initial UI's speed-cycle button.
- The first UI provides a user-initiated `audio/*` document picker and sends the selected content URI to the service. It attempts to persist the read URI grant and tolerates providers that do not offer a persistable grant.

## Scope and known gaps

- This is the first playback slice, not the complete library implementation. Issue #4 will own durable library import, item metadata, and handling unavailable URIs.
- The UI is intentionally minimal. It does not yet include a seek bar, playlist/queue management, or a detailed error/retry surface.
- The notification permission is declared, but the Activity does not yet request it at runtime. Android may apply notification visibility rules based on the user's permission and OS version.
- The service/session and manifest are compile-tested by CI only until physical-device testing is performed. Notification, lock-screen, Bluetooth/headphone, audio-focus interruptions, screen-off playback, and sample-format compatibility must be verified on the target phone under Issue #7.
- Force-stop, OS process termination, reboot, and manufacturer-specific battery policies are not guaranteed to preserve uninterrupted playback.

## Dependency

AndroidX Media3 ExoPlayer and MediaSession are pinned together to version `1.8.0` in `app/build.gradle.kts`. Upgrade them together and run the Android build/tests when changing versions.
