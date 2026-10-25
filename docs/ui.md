# Persian RTL interface

**Original implementation:** [#6 — Persian RTL library and player screens](https://github.com/yusi20006-max/YasinCast/issues/6)  
**Visual refresh:** [#17 — lapis glass theme](https://github.com/yusi20006-max/YasinCast/issues/17)

## Visual system

The refreshed interface uses a dark navy base, lapis-blue primary actions, cool cyan highlights, and glass-like panels with subtle blue borders. The player displays embedded cover art and audio tags when available, with a lapis-themed fallback when artwork is absent. Navigation uses a clear selected state. All labels remain Persian-first and the screen stays RTL.

The palette is defined in UiTheme.kt and is intentionally local/offline: no remote fonts, artwork, or network resources are required.

## Screens

- **کتابخانه:** import audio files or a folder using Android's Storage Access Framework; browse saved items; resume, restart, or remove an item from the app's list without deleting the source.
- **در حال پخش:** embedded cover art, title, artist/author, album, duration when known, seek slider, elapsed and remaining time, play/pause, skip backward/forward 15 seconds, and playback-speed control.
- Embedded title, artist, album, and cover are read locally from the selected file. The same title/artist/album and artwork bytes are attached to the Media3 media item for Android system media surfaces when supported. Missing or unreadable tags fall back to the file name; missing artwork uses the built-in lapis placeholder.
- **تنظیمات:** current playback speed, a speed-change action, current status, app/offline note, and recent listening events.

## RTL and accessibility

The Compose screen content explicitly uses right-to-left layout direction. Labels and status messages are Persian-first. Primary actions use Material 3 buttons, cards group related content, disabled controls indicate unavailable actions, and the seek slider is disabled until a selected item has a known duration. Missing metadata is shown as a fallback rather than treated as a fatal error.

## State ownership

The Media3 MediaController remains the source of truth for play/pause, playback position, duration, speed, and seeking. The UI sends actions to the activity/controller and receives observable state. The progress label is refreshed while playing; persistence continues through the Room-backed repository introduced in Issue #5.

## Verification status and limitations

- Automated verification is provided by the Android GitHub Actions workflow (assembleDebug and testDebugUnitTest).
- The current UI is a focused MVP; settings are intentionally limited and do not introduce accounts or online features. The lapis/glass visual refresh is a presentation-only layer over the existing playback and persistence behavior.
- Dynamic font scaling, TalkBack traversal, small-screen/landscape behavior, and real-device Persian rendering must still be checked on the target Android phone under Issue #7.
- Metadata and cover art may be unavailable for unsupported, inaccessible, or untagged files; the player shows filename/placeholder fallbacks and playback remains available.
