package com.yasin.cast

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.OpenableColumns
import android.net.Uri
import java.util.Locale

/**
 * Small local index of user-selected SAF URIs. The original media is never copied or modified.
 * Playback progress/history is intentionally left for Issue #5.
 */
data class AudioLibraryEntry(
    val uri: String,
    val title: String,
    val mimeType: String?,
    val sizeBytes: Long?
)

class AudioLibraryStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE (
                uri TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                mime_type TEXT,
                size_bytes INTEGER
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add explicit migrations here when the schema changes; never discard user library data.
    }

    fun all(): List<AudioLibraryEntry> {
        val entries = mutableListOf<AudioLibraryEntry>()
        readableDatabase.query(
            TABLE,
            arrayOf(COL_URI, COL_TITLE, COL_MIME, COL_SIZE),
            null,
            null,
            null,
            null,
            "$COL_TITLE COLLATE NOCASE ASC"
        ).use { cursor ->
            val uriIndex = cursor.getColumnIndexOrThrow(COL_URI)
            val titleIndex = cursor.getColumnIndexOrThrow(COL_TITLE)
            val mimeIndex = cursor.getColumnIndexOrThrow(COL_MIME)
            val sizeIndex = cursor.getColumnIndexOrThrow(COL_SIZE)
            while (cursor.moveToNext()) {
                entries += AudioLibraryEntry(
                    uri = cursor.getString(uriIndex),
                    title = cursor.getString(titleIndex),
                    mimeType = cursor.getString(mimeIndex),
                    sizeBytes = if (cursor.isNull(sizeIndex)) null else cursor.getLong(sizeIndex)
                )
            }
        }
        return entries
    }

    fun upsert(entry: AudioLibraryEntry) {
        val values = ContentValues().apply {
            put(COL_URI, entry.uri)
            put(COL_TITLE, entry.title)
            put(COL_MIME, entry.mimeType)
            if (entry.sizeBytes == null) putNull(COL_SIZE) else put(COL_SIZE, entry.sizeBytes)
        }
        writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun remove(uri: String) {
        writableDatabase.delete(TABLE, "$COL_URI = ?", arrayOf(uri))
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        private const val DATABASE_NAME = "yasin_cast_library.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE = "audio_library"
        private const val COL_URI = "uri"
        private const val COL_TITLE = "title"
        private const val COL_MIME = "mime_type"
        private const val COL_SIZE = "size_bytes"
    }
}

internal object AudioLibraryRules {
    private val supportedExtensions = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav", "wave")

    fun isAudio(mimeType: String?, displayName: String?): Boolean {
        if (!mimeType.isNullOrBlank() && mimeType.lowercase(Locale.ROOT).startsWith("audio/")) return true
        val extension = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return extension in supportedExtensions
    }

    fun formatSize(bytes: Long?): String {
        if (bytes == null || bytes < 0L) return "حجم نامشخص"
        if (bytes < 1024L) return "$bytes بایت"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f کیلوبایت", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.ROOT, "%.1f مگابایت", mb)
        return String.format(Locale.ROOT, "%.2f گیگابایت", mb / 1024.0)
    }
}

/** Query the provider's best display name and optional size for a selected content URI. */
internal fun queryAudioEntry(context: Context, uri: Uri): AudioLibraryEntry {
    var title = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "فایل صوتی"
    var size: Long? = null
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val titleIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (titleIndex >= 0 && !cursor.isNull(titleIndex)) {
                        cursor.getString(titleIndex)?.takeIf { it.isNotBlank() }?.let { title = it }
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
    } catch (_: Exception) {
        // Some document providers do not expose metadata; retain a usable fallback title.
    }
    return AudioLibraryEntry(
        uri = uri.toString(),
        title = title,
        mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull(),
        sizeBytes = size
    )
}
