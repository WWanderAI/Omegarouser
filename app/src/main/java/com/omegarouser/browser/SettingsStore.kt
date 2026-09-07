package com.omegarouser.browser

import android.content.Context
import android.net.Uri

object SettingsStore {

    private const val PREFS_NAME = "omegarouser_prefs"
    private const val KEY_ADBLOCK = "adblock_enabled"
    private const val KEY_SEARCH_ENGINE = "search_engine" // "google" | "yandex" | "duckduckgo"

    fun isAdblockEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ADBLOCK, true)
    }

    fun setAdblockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ADBLOCK, enabled).apply()
    }

    fun getSearchEngine(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SEARCH_ENGINE, "google") ?: "google"
    }

    fun setSearchEngine(context: Context, engine: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SEARCH_ENGINE, engine).apply()
    }

    fun searchUrl(context: Context, query: String): String {
        val encoded = Uri.encode(query)
        return when (getSearchEngine(context)) {
            "yandex" -> "https://yandex.ru/search/?text=$encoded"
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }

    fun homeSearchAction(context: Context): String {
        return when (getSearchEngine(context)) {
            "yandex" -> "https://yandex.ru/search/"
            "duckduckgo" -> "https://duckduckgo.com/"
            else -> "https://www.google.com/search"
        }
    }
}
