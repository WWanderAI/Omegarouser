package com.omegarouser.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val url: String,
    val title: String,
    val timestamp: Long
)

object HistoryStore {

    private const val PREFS_NAME = "omegarouser_prefs"
    private const val KEY_HISTORY = "history"
    private const val MAX_ENTRIES = 300

    fun addEntry(context: Context, url: String, title: String) {
        if (url.isBlank() || url.startsWith("file:///android_asset")) return

        val entries = getEntries(context).toMutableList()

        // Не дублируем подряд идущие посещения одной и той же страницы
        if (entries.isNotEmpty() && entries[0].url == url) return

        entries.add(0, HistoryEntry(url, title.ifBlank { url }, System.currentTimeMillis()))

        val trimmed = if (entries.size > MAX_ENTRIES) entries.subList(0, MAX_ENTRIES) else entries
        save(context, trimmed)
    }

    fun getEntries(context: Context): List<HistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<HistoryEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    HistoryEntry(
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
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun save(context: Context, entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach {
            val obj = JSONObject()
            obj.put("url", it.url)
            obj.put("title", it.title)
            obj.put("timestamp", it.timestamp)
            arr.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }
}
