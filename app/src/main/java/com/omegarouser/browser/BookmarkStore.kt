package com.omegarouser.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BookmarkEntry(
    val url: String,
    val title: String,
    val timestamp: Long
)

object BookmarkStore {

    private const val PREFS_NAME = "omegarouser_prefs"
    private const val KEY_BOOKMARKS = "bookmarks"

    fun isBookmarked(context: Context, url: String): Boolean {
        return getEntries(context).any { it.url == url }
    }

    fun toggle(context: Context, url: String, title: String): Boolean {
        val entries = getEntries(context).toMutableList()
        val existing = entries.indexOfFirst { it.url == url }
        return if (existing >= 0) {
            entries.removeAt(existing)
            save(context, entries)
            false
        } else {
            entries.add(0, BookmarkEntry(url, title.ifBlank { url }, System.currentTimeMillis()))
            save(context, entries)
            true
        }
    }

    fun remove(context: Context, url: String) {
        val entries = getEntries(context).filter { it.url != url }
        save(context, entries)
    }

    fun getEntries(context: Context): List<BookmarkEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<BookmarkEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    BookmarkEntry(
                        url = obj.getString("url"),
                        title = obj.getString("title"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_BOOKMARKS).apply()
    }

    private fun save(context: Context, entries: List<BookmarkEntry>) {
        val arr = JSONArray()
        entries.forEach {
            val obj = JSONObject()
            obj.put("url", it.url)
            obj.put("title", it.title)
            obj.put("timestamp", it.timestamp)
            arr.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BOOKMARKS, arr.toString()).apply()
    }
}
