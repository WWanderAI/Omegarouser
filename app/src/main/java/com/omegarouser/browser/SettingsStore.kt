package com.omegarouser.browser

import android.content.Context
import android.net.Uri

object SettingsStore {

    private const val PREFS_NAME = "omegarouser_prefs"
    private const val KEY_ADBLOCK = "adblock_enabled"
    private const val KEY_SEARCH_ENGINE = "search_engine" // "google" | "yandex" | "duckduckgo"
    private const val KEY_CSE_API_KEY = "cse_api_key"
    private const val KEY_CSE_ENGINE_ID = "cse_engine_id"

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

    fun getCustomSearchApiKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CSE_API_KEY, "") ?: ""
    }

    fun setCustomSearchApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CSE_API_KEY, key.trim()).apply()
    }

    fun getCustomSearchEngineId(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CSE_ENGINE_ID, "") ?: ""
    }

    fun setCustomSearchEngineId(context: Context, id: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CSE_ENGINE_ID, id.trim()).apply()
    }

    fun isCustomSearchConfigured(context: Context): Boolean {
        return getCustomSearchApiKey(context).isNotBlank() && getCustomSearchEngineId(context).isNotBlank()
    }

    fun searchUrl(context: Context, query: String): String {
        if (isCustomSearchConfigured(context)) {
            return "file:///android_asset/search_results.html?q=" + Uri.encode(query)
        }
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
