# Physical-device acceptance checklist (Issue #7)

**Status:** Prepared; execution on the owner's physical Android device is pending. Do not mark a test as passed until its evidence is recorded.

## Test session record

- Date/time:
- Tester:
- Device model:
- Android version / build:
- App version / commit:
- APK artifact/run URL:
- Battery optimization state for YasinCast:
- Notification permission state:
- Network state during offline tests:

## Evidence rules

For each test, record **PASS / FAIL / BLOCKED / NOT RUN**, the exact steps, observed result, and evidence (screenshot, screen recording, logcat excerpt, or reproducible notes). Avoid recording personal audio content or private notification text in shared evidence. File reproducible failures as focused GitHub issues and link them below.

## A. Install and lifecycle

- [ ] Install debug APK on a clean or documented existing install.
- [ ] Launch app; confirm no crash and Persian RTL text is readable.
- [ ] Background and return to app.
- [ ] Close and relaunch; confirm library and playback progress remain available.
- [ ] Record device and Android build details above.

## B. Local import and file compatibility

Test files should be non-sensitive and stored locally. Record exact container/codec for each sample.

| Sample | Format / codec | Import | Playback | Seek | Notes |
|---|---|---|---|---|---|
| Sample 1 | MP3 | NOT RUN | NOT RUN | NOT RUN | |
| Sample 2 | M4A/AAC | NOT RUN | NOT RUN | NOT RUN | |
| Sample 3 | WAV | NOT RUN | NOT RUN | NOT RUN | |
| Sample 4 | OGG/Opus (if available) | NOT RUN | NOT RUN | NOT RUN | |

- [ ] Import multiple files through the system picker.
- [ ] Import a folder if the provider offers folder selection.
- [ ] Verify remove-from-library does not delete the source file.
- [ ] Try a missing/unavailable URI and a known unsupported or corrupt sample; record actual behavior.

## C. Playback controls

- [ ] Play and pause.
- [ ] Seek using the slider.
- [ ] Skip backward and forward 15 seconds, including near start/end boundaries.
- [ ] Cycle through speed settings; verify 0.8x through 2.0x and actual audible rate.
- [ ] Confirm displayed title and duration; note metadata fallbacks.
- [ ] Confirm elapsed/remaining position display is reasonable.

## D. Persistence and history

- [ ] Play beyond 5 seconds, exit the screen/background the app, return, and verify resume position.
- [ ] Force-stop only as a separate destructive lifecycle test; record whether progress was saved before the stop.
- [ ] Reach the completion threshold for a sample of at least 30 seconds; verify played status.
- [ ] Verify recent listening history survives app relaunch.
- [ ] Repeat after device restart only if practical; distinguish expected saved state from uninterrupted playback.

## E. Background and system integration

- [ ] Start playback and turn screen off for at least 2 minutes.
- [ ] Verify media notification appears as permitted by Android settings.
- [ ] Verify notification and lock-screen play/pause/seek controls where supported.
- [ ] Test wired headset or Bluetooth play/pause controls if available.
- [ ] Disconnect headphones/Bluetooth during playback; verify expected pause/noisy-output behavior.
- [ ] Interrupt with a phone call or another audio app; record audio-focus behavior and recovery.
- [ ] Test battery-saver / manufacturer background restrictions separately if playback stops unexpectedly.

## F. Offline and RTL usability

- [ ] Enable airplane mode after installation; import from local storage and play local files.
- [ ] Confirm no app feature requires network access for local playback.
- [ ] Inspect all three tabs: کتابخانه، در حال پخش، تنظیمات.
- [ ] Check long Persian titles, mixed Persian/Latin filenames, digits, and missing metadata.
- [ ] Check large system font size and display scaling.
- [ ] Check TalkBack focus order and actionable labels.
- [ ] Check portrait and landscape layouts if landscape is supported/expected.
- [ ] Confirm buttons and sliders are reachable and not clipped on the target phone.

## Results summary

| Area | Status | Evidence / issue |
|---|---|---|
| Install / lifecycle | NOT RUN | |
| Import / formats | NOT RUN | |
| Player controls | NOT RUN | |
| Progress / history | NOT RUN | |
| Background / system controls | NOT RUN | |
| Offline mode | NOT RUN | |
| Persian RTL / accessibility | NOT RUN | |

## Known caveats before testing

- CI build and unit tests prove compilation and the current unit-test suite only; they do not prove device-specific playback reliability.
- Android notification visibility depends on OS version and permission/settings.
- Force-stop, process death, reboot, and manufacturer battery policies may interrupt playback.
- Do not close Issue #7 or claim the MVP is physically validated until actual test evidence is attached and failures are triaged.
