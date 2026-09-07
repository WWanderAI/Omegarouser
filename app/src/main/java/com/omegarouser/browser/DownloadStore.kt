package com.omegarouser.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DownloadEntry(
    val downloadManagerId: Long,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val timestamp: Long
)

object DownloadStore {

    private const val PREFS_NAME = "omegarouser_prefs"
    private const val KEY_DOWNLOADS = "downloads"
    private const val MAX_ENTRIES = 200

    fun addEntry(context: Context, id: Long, fileName: String, url: String, mimeType: String) {
        val entries = getEntries(context).toMutableList()
        entries.add(0, DownloadEntry(id, fileName, url, mimeType, System.currentTimeMillis()))
        val trimmed = if (entries.size > MAX_ENTRIES) entries.subList(0, MAX_ENTRIES) else entries
        save(context, trimmed)
    }

    fun getEntries(context: Context): List<DownloadEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<DownloadEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    DownloadEntry(
                        downloadManagerId = obj.getLong("id"),
                        fileName = obj.getString("fileName"),
                        url = obj.getString("url"),
                        mimeType = obj.optString("mimeType", "*/*"),
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
        prefs.edit().remove(KEY_DOWNLOADS).apply()
    }

    private fun save(context: Context, entries: List<DownloadEntry>) {
        val arr = JSONArray()
        entries.forEach {
            val obj = JSONObject()
            obj.put("id", it.downloadManagerId)
            obj.put("fileName", it.fileName)
            obj.put("url", it.url)
            obj.put("mimeType", it.mimeType)
            obj.put("timestamp", it.timestamp)
            arr.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DOWNLOADS, arr.toString()).apply()
    }
}
