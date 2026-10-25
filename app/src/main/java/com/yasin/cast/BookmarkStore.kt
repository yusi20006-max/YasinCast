package com.yasin.cast

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AudioBookmark(
    val id: Long,
    val uri: String,
    val title: String,
    val positionMs: Long,
    val note: String,
    val createdAt: Long
)

/** Lightweight private local storage; bookmark notes never leave the device. */
class BookmarkStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("audio_bookmarks", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<AudioBookmark> {
        val array = runCatching { JSONArray(preferences.getString(KEY_ITEMS, "[]")) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                AudioBookmark(
                    id = item.getLong("id"),
                    uri = item.getString("uri"),
                    title = item.getString("title"),
                    positionMs = item.getLong("positionMs").coerceAtLeast(0L),
                    note = item.optString("note").take(240),
                    createdAt = item.optLong("createdAt")
                )
            }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun add(uri: String, title: String, positionMs: Long, note: String): AudioBookmark {
        val items = all().toMutableList()
        val bookmark = AudioBookmark(
            id = (items.maxOfOrNull { it.id } ?: 0L) + 1L,
            uri = uri,
            title = title,
            positionMs = positionMs.coerceAtLeast(0L),
            note = note.trim().take(240),
            createdAt = System.currentTimeMillis()
        )
        items.add(bookmark)
        save(items)
        return bookmark
    }

    @Synchronized
    fun remove(id: Long) = save(all().filterNot { it.id == id })

    @Synchronized
    fun forUri(uri: String): List<AudioBookmark> = all().filter { it.uri == uri }.sortedBy { it.positionMs }

    private fun save(items: List<AudioBookmark>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("uri", item.uri)
                put("title", item.title)
                put("positionMs", item.positionMs)
                put("note", item.note)
                put("createdAt", item.createdAt)
            })
        }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    companion object {
        private const val KEY_ITEMS = "items"
    }
}
