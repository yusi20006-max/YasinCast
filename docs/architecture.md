# YasinCast Architecture

**Status:** Proposed offline MVP architecture  
**Tracking issue:** [#1 — architecture and phased roadmap](https://github.com/yusi20006-max/YasinCast/issues/1)

## Product goal and decision

YasinCast is a Persian-first native Android app for organizing and listening to locally stored podcasts and audio episodes. Phase 1 must work offline. Online hosting, accounts, publishing, and synchronization are deferred until the offline MVP passes physical-device verification.

Use Kotlin, Jetpack Compose, AndroidX Media3, Android's Storage Access Framework (SAF), Room, and DataStore. Issue #5 introduces Room-backed library/playback-state/history storage; simple preferences remain a separate future DataStore concern. Native Android is preferred over a standalone PWA because background audio, lock-screen/system controls, audio focus, and headset/Bluetooth behavior are core requirements.

## Layer boundaries

1. **Presentation:** Compose screens for library, now playing, and settings; Persian-first right-to-left layout. UI observes state and sends intents; it does not own the player lifecycle.
2. **Domain:** focused use cases/interfaces for importing media, playback actions, resume position, history, bookmarks, search, and sleep timer. Keep domain-facing interfaces independent of UI classes where practical.
3. **Playback:** Media3 ExoPlayer hosted by MediaSessionService. MediaSession integrates transport controls with notifications, lock screen, headsets/Bluetooth, and compatible system clients. Handle audio focus and noisy-output events.
4. **Local media access:** SAF file/document and directory pickers. Persist URI permissions when granted. Do not request broad storage access just to scan the device. URI permissions may be revoked and files may disappear.
5. **Persistence:** Room for audio-item metadata, app IDs, playback position, played/completed state, history, and bookmarks. DataStore for simple preferences such as playback speed and sleep-timer defaults. Add explicit migrations and tests when schemas change.
6. **Future online boundary:** remote catalog, authentication, upload/hosting, RSS, and sync remain out of scope. Later add remote data sources behind stable interfaces instead of coupling UI/player directly to a remote API.

## Initial dependency choices

Choose mutually compatible stable versions at implementation time, after inspecting the actual Android/Gradle toolchain. Do not pin unverified versions here.

- Kotlin, Android Gradle Plugin, and a committed Gradle Wrapper.
- Jetpack Compose and Material 3.
- AndroidX Media3 ExoPlayer, MediaSession, and service integration.
- AndroidX Room with one supported compiler integration, selected deliberately.
- AndroidX DataStore.
- AndroidX lifecycle/ViewModel integration as needed.
- JUnit and AndroidX unit/instrumentation testing tools.
- GitHub Actions for a reproducible debug build once the Android project exists (Issue #2).

Avoid alpha/beta dependencies unless a documented blocker requires them. Do not upgrade global Termux packages or alter unrelated projects to satisfy this repository.

## MVP data model direction

- **AudioItem:** app ID, content URI, display title, optional artist/collection, duration when readable, MIME/type when available, import date, availability state.
- **PlaybackState:** audio item ID, last position, last played timestamp, played/completed status.
- **ListeningEvent:** item ID, timestamp, and event type/duration as appropriate for useful local history.
- **Bookmark:** ID, item ID, position, optional note, creation timestamp.
- **Preferences:** playback speed and user defaults, stored in DataStore rather than Room when they are simple settings.

Metadata must be nullable when the source cannot provide it. Do not promise complete tags for every file.

## Media and format policy

The intended test set is MP3, M4A/AAC, OGG, FLAC, and WAV. Actual playback depends on Android/Media3 decoder support, container details, device codecs, and encoding. Advertise support only for formats that pass tests with representative files on the target phone. Unsupported or corrupt media must produce a useful error rather than a crash.

## Security, privacy, and reliability

- Offline MVP must not require an account, server, analytics SDK, or network permission unless a concrete need is documented.
- Access local audio through user-selected SAF URIs; never modify or delete source audio.
- Never commit secrets, tokens, private audio, or user data.
- Keep UI, persistence, and playback lifecycles separate.
- Handle missing/revoked URI grants, corrupt files, empty libraries, and interrupted playback.
- Do not claim uninterrupted playback after force-stop, process termination, reboot, or manufacturer-specific battery restrictions without evidence. Verify normal background/screen-off behavior on a physical device.
- Follow Android notification and foreground-service requirements for the selected target SDK.

## MVP acceptance criteria

- A reproducible debug APK can be built from the committed Gradle project.
- User can select local files/folders via SAF and see available items in a Persian RTL library.
- Playback, pause, seek, ±15-second skips, and 0.8x–2.0x speed controls work.
- Playback continues with screen off under normal OS conditions and exposes system media controls.
- Audio focus and noisy-output changes are handled.
- Last position and played state survive app restarts.
- Sleep timer, bookmarks with optional notes, and local title search work.
- Imported library and playback work in airplane mode.
- Automated tests pass and Issue #7 records actual physical-device outcomes without unsupported claims.

## Explicitly out of scope for offline MVP

Remote audio storage/streaming, public podcast pages, RSS publishing/import, accounts/login, payments, cloud backup, cross-device sync, analytics, and web administration. These require a separate design and security/cost review after the offline MVP passes.
