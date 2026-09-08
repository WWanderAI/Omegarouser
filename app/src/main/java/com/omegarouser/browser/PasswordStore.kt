package com.omegarouser.browser

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class Credential(
    val site: String,   // хост, например "vk.com"
    val username: String,
    val password: String,
    val timestamp: Long
)

/**
 * Хранит логины/пароли в зашифрованном виде (Android Keystore + AES256-GCM).
 * Файл на диске зашифрован полностью — при извлечении с телефона без ключа
 * устройства прочитать пароли нельзя.
 */
object PasswordStore {

    private const val FILE_NAME = "omegarouser_passwords_enc"
    private const val KEY_CREDENTIALS = "credentials"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(context: Context, site: String, username: String, password: String) {
        val list = getAll(context).toMutableList()
        list.removeAll { it.site == site && it.username == username }
        list.add(0, Credential(site, username, password, System.currentTimeMillis()))
        persist(context, list)
    }

    fun remove(context: Context, site: String, username: String) {
        val list = getAll(context).filter { !(it.site == site && it.username == username) }
        persist(context, list)
    }

    fun getAll(context: Context): List<Credential> {
        val raw = try {
            getPrefs(context).getString(KEY_CREDENTIALS, null)
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<Credential>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    Credential(
                        site = obj.getString("site"),
                        username = obj.getString("username"),
                        password = obj.getString("password"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun findForHost(context: Context, host: String): Credential? {
        return getAll(context).firstOrNull { host.contains(it.site) || it.site.contains(host) }
    }

    private fun persist(context: Context, list: List<Credential>) {
        val arr = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("site", it.site)
            obj.put("username", it.username)
            obj.put("password", it.password)
            obj.put("timestamp", it.timestamp)
            arr.put(obj)
        }
        getPrefs(context).edit().putString(KEY_CREDENTIALS, arr.toString()).apply()
    }
}
