package com.yasin.cast

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "audio_library")
data class AudioItemEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastPositionMs: Long = 0L,
    val durationMs: Long? = null,
    val played: Boolean = false,
    val lastPlayedAt: Long? = null,
    val importedAt: Long = System.currentTimeMillis()
) {
    fun toLibraryEntry() = AudioLibraryEntry(uri, title, mimeType, sizeBytes)
}

@Entity(
    tableName = "listening_history",
    indices = [Index(value = ["playedAt"]), Index(value = ["uri", "playedAt"])]
)
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uri: String,
    val title: String,
    val playedAt: Long,
    val eventType: String,
    val positionMs: Long
)

@Dao
interface AudioItemDao {
    @Query("SELECT * FROM audio_library ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAll(): List<AudioItemEntity>

    @Query("SELECT * FROM audio_library WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): AudioItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AudioItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(item: AudioItemEntity)

    @Query("DELETE FROM audio_library WHERE uri = :uri")
    suspend fun remove(uri: String)

    @Query("UPDATE audio_library SET lastPositionMs = :positionMs, durationMs = COALESCE(:durationMs, durationMs) WHERE uri = :uri")
    suspend fun saveProgress(uri: String, positionMs: Long, durationMs: Long?)

    @Query("UPDATE audio_library SET played = :played, lastPlayedAt = :playedAt, lastPositionMs = :positionMs, durationMs = :durationMs WHERE uri = :uri")
    suspend fun saveCompletion(uri: String, played: Boolean, playedAt: Long, positionMs: Long, durationMs: Long?)

    @Query("UPDATE audio_library SET played = 0, lastPositionMs = 0 WHERE uri = :uri")
    suspend fun resetPlayback(uri: String)

    @Query("SELECT * FROM audio_library WHERE played = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getPlayed(limit: Int): List<AudioItemEntity>
}

@Dao
interface ListeningHistoryDao {
    @Insert
    suspend fun insert(event: ListeningEventEntity)

    @Query("SELECT * FROM listening_history ORDER BY playedAt DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ListeningEventEntity>

    @Query("DELETE FROM listening_history WHERE id NOT IN (SELECT id FROM listening_history ORDER BY playedAt DESC, id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Database(
    entities = [AudioItemEntity::class, ListeningEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class YasinCastDatabase : RoomDatabase() {
    abstract fun audioItems(): AudioItemDao
    abstract fun listeningHistory(): ListeningHistoryDao

    companion object {
        @Volatile private var instance: YasinCastDatabase? = null

        fun get(context: Context): YasinCastDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    YasinCastDatabase::class.java,
                    "yasin_cast_room.db"
                ).build().also { instance = it }
            }
    }
}

class AudioLibraryRepository private constructor(private val context: Context) {
    private val database = YasinCastDatabase.get(context)
    private val items = database.audioItems()
    private val history = database.listeningHistory()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        importLegacyLibraryOnce()
    }

    suspend fun all(): List<AudioItemEntity> = withContext(Dispatchers.IO) { items.getAll() }

    suspend fun upsert(entry: AudioLibraryEntry) = withContext(Dispatchers.IO) {
        val previous = items.getByUri(entry.uri)
        items.upsert(
            AudioItemEntity(
                uri = entry.uri,
                title = entry.title,
                mimeType = entry.mimeType,
                sizeBytes = entry.sizeBytes,
                lastPositionMs = previous?.lastPositionMs ?: 0L,
                durationMs = previous?.durationMs,
                played = previous?.played ?: false,
                lastPlayedAt = previous?.lastPlayedAt,
                importedAt = previous?.importedAt ?: System.currentTimeMillis()
            )
        )
    }

    suspend fun remove(uri: String) = withContext(Dispatchers.IO) { items.remove(uri) }

    suspend fun playbackState(uri: String): AudioItemEntity? = withContext(Dispatchers.IO) { items.getByUri(uri) }

    suspend fun saveProgress(uri: String, positionMs: Long, durationMs: Long?) = withContext(Dispatchers.IO) {
        items.saveProgress(uri, positionMs.coerceAtLeast(0L), durationMs?.takeIf { it > 0L })
    }

    suspend fun markPlayed(uri: String, title: String, positionMs: Long, durationMs: Long?) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        items.saveCompletion(uri, true, now, positionMs.coerceAtLeast(0L), durationMs?.takeIf { it > 0L })
        history.insert(ListeningEventEntity(uri = uri, title = title, playedAt = now, eventType = "completed", positionMs = positionMs))
        history.trim(MAX_HISTORY)
    }

    suspend fun recordStarted(uri: String, title: String, positionMs: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val previous = items.getByUri(uri)
        if (previous != null) items.upsert(previous.copy(lastPlayedAt = now))
        history.insert(ListeningEventEntity(uri = uri, title = title, playedAt = now, eventType = "started", positionMs = positionMs.coerceAtLeast(0L)))
        history.trim(MAX_HISTORY)
    }

    suspend fun resetPlayback(uri: String) = withContext(Dispatchers.IO) { items.resetPlayback(uri) }

    suspend fun recentHistory(limit: Int = 30): List<ListeningEventEntity> =
        withContext(Dispatchers.IO) { history.recent(limit.coerceIn(1, MAX_HISTORY)) }

    private suspend fun importLegacyLibraryOnce() {
        val prefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(LEGACY_IMPORTED, false)) return
        val legacyFile = context.getDatabasePath("yasin_cast_library.db")
        if (legacyFile.exists()) {
            runCatching {
                SQLiteDatabase.openDatabase(legacyFile.path, null, SQLiteDatabase.OPEN_READONLY).use { legacy ->
                    legacy.rawQuery(
                        "SELECT uri, title, mime_type, size_bytes FROM audio_library",
                        null
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            items.insertIfMissing(
                                AudioItemEntity(
                                    uri = cursor.getString(0),
                                    title = cursor.getString(1),
                                    mimeType = if (cursor.isNull(2)) null else cursor.getString(2),
                                    sizeBytes = if (cursor.isNull(3)) null else cursor.getLong(3)
                                )
                            )
                        }
                    }
                }
            }.getOrThrow()
        }
        prefs.edit().putBoolean(LEGACY_IMPORTED, true).apply()
    }

    companion object {
        private const val MAX_HISTORY = 200
        private const val MIGRATION_PREFS = "yasin_cast_room_migration"
        private const val LEGACY_IMPORTED = "legacy_library_imported"

        fun create(context: Context) = AudioLibraryRepository(context.applicationContext)
    }
}

internal object PlaybackCompletionRules {
    const val COMPLETION_FRACTION = 0.90
    const val MIN_DURATION_MS = 30_000L

    fun isCompleted(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs < MIN_DURATION_MS || positionMs < 0L) return false
        return positionMs.toDouble() / durationMs.toDouble() >= COMPLETION_FRACTION
    }

    fun resumePosition(positionMs: Long, durationMs: Long?): Long {
        if (positionMs <= 0L) return 0L
        if (durationMs != null && durationMs > 0L && positionMs >= durationMs) return 0L
        return positionMs
    }
}
