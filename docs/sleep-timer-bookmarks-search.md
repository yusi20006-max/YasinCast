# Sleep timer, bookmarks, and library search

**Tracking issue:** [#8 — sleep timer, bookmarks, and library search](https://github.com/yusi20006-max/YasinCast/issues/8)

## Sleep timer
The Settings tab offers 15, 30, 45, 60, and 90 minute presets plus cancellation. The timer is owned by `PlaybackService`, not the Activity, so it can pause Media3 playback after the selected interval while the screen is off and the service remains alive. The timer is in-memory and is cleared if Android terminates the playback service/process; it is not a wall-clock alarm that survives a force-stop or reboot.

## Bookmarks
In the player tab, save a bookmark at the current playback position with an optional note (up to 240 characters). Bookmarks are stored locally in private app preferences and include the selected document URI, title, position, and creation time. Select a bookmark to seek to that position; bookmarks can be deleted. No notes or listening data are sent to a server. If the original document is removed or its URI permission is revoked, the bookmark remains but playback may require re-importing the file.

## Search
The library tab filters the imported local index by display title and URI filename. Search normalizes common Persian/Arabic letter variants (ي/ی, ك/ک, ة/ه) and removes Arabic diacritics. It does not scan file contents or fetch online metadata.

## Verification
The Android CI workflow runs `assembleDebug` and `testDebugUnitTest`. Device-level timer behavior with the screen off, bookmark seeking after process restart, and Persian keyboard/search behavior should be checked on the target phone as part of physical-device acceptance.
