# Local audio library (Issue #4)

## User flow

- **Add files** opens Android's Storage Access Framework picker for multiple `audio/*` documents.
- **Add folder** requests a user-selected document tree and walks its child documents where the provider supports tree traversal. Traversal is bounded to depth 12 and 5,000 visited documents to avoid unbounded scans.
- Selected document URIs, display titles, MIME types when exposed, and sizes when exposed are indexed in the app's private SQLite database. Entries are keyed by URI, so importing the same URI again updates rather than duplicates it.
- The app attempts to persist read grants. If a provider does not support persistent grants, the app tolerates that limitation and checks readability before playback.
- Selecting a library row plays that URI through the existing Media3 service. Removing an entry removes only the app's index record; it never deletes the original audio or explicitly revokes URI permissions.
- Empty-library state and recoverable messages are shown for unreadable/missing documents and playback failures.

## File recognition and playback support

The picker requests `audio/*`; when providers omit a useful MIME type, the index also recognizes common extensions: MP3, M4A, AAC, OGG/OGA/Opus, FLAC, WAV/WAVE. This is an import filter, **not a guarantee that every file decodes**. Actual playback depends on the Android version, Media3 decoder support, file encoding, and device codecs. The UI surfaces playback errors instead of claiming unsupported formats work.

The selected folder provider must expose document-tree traversal. Some providers return no children or deny queries; in that case the app reports that no files were found or that the folder could not be read. The implementation does not scan all device storage and does not copy media into app storage.

## Persistence and known gaps

- The SQLite index survives Activity/app restarts. A URI can still become unusable if the user deletes or moves the source, revokes access, or the provider stops serving it. Such entries can be removed from the app's list and imported again after access is restored.
- Library metadata is intentionally limited to provider display name, MIME type, and size when available. Embedded tags, duration, playlists, and richer metadata are deferred.
- Playback position, played/completed state, and recent listening events are now handled by the Room-backed repository described in [history.md](history.md).
- Automated unit tests cover MIME/extension recognition and size labels. Android SAF behavior, folder-provider compatibility, decoder support, and real-device playback remain unverified until Issue #7 is performed on the target phone.
