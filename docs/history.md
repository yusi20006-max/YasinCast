# Playback progress and listening history (Issue #5)

## Local Room database

The app uses Room in `yasin_cast_room.db` with two tables:

- `audio_library`: the SAF URI (primary key), title, MIME type, size, imported time, last saved position, duration when known, completion/played flag, and last-played time.
- `listening_history`: timestamped `started` and `completed` events, URI/title, and position. The app retains at most the 200 most recent events.

Database writes and reads in the repository are dispatched to `Dispatchers.IO`; no Room query is configured to run on the main thread. The current schema version is 1. Future schema changes must increment the Room version and include an explicit, data-preserving migration plus tests.

## Existing library migration

Issue #4 stored library entries in `yasin_cast_library.db` via SQLiteOpenHelper. On first initialization, the repository imports those entries into Room with insert-if-missing semantics. The original database is not deleted or modified. A private app preference records successful completion so the import is not repeated on every launch. Existing URIs and file metadata are preserved; playback progress begins at zero because the legacy schema did not contain progress fields.

## Resume and completion rules

- While playing, the current position is checkpointed every 5 seconds; position is also saved when playback pauses and when the Activity stops. Writes are asynchronous on the I/O dispatcher.
- Starting an item restores its saved position, unless the saved position is at or beyond the known duration, in which case playback starts from zero.
- An item is marked **played/completed** when it reaches at least 90% of its known duration, provided the duration is at least 30 seconds. The same rule is checked during five-second progress checkpoints and at end-of-playback.
- The library exposes saved progress and played status. **From start** resets the played flag and saved position, then plays the item again.
- Recent history shows the latest local start/completion events. No account, network sync, analytics, or cloud storage is involved.

## Reliability and limits

Room stores progress checkpoints, not a guarantee of exact sample-level recovery. A force-stop, process kill, crash, or power loss can lose up to approximately one checkpoint interval of progress. The Activity saves on stop, but playback service/process lifecycle behavior still requires physical-device validation. Removing an item removes its library row; historical events are retained as a lightweight local log. This issue does not add a history screen, export, per-item history deletion, or cross-device synchronization.

Automated tests cover completion thresholds and resume-position normalization. Database migration/import, background playback, and restart recovery should be validated on a real Android device as part of Issue #7.
