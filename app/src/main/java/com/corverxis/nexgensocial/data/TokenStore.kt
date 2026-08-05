package com.corverxis.nexgensocial.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.corverxis.nexgensocial.network.ApiClient

/**
 * Encrypted storage for the auth token.
 *
 * Plain SharedPreferences would be simpler, but it's readable on a rooted
 * device and can end up in cloud backups. An auth token is a credential, so
 * it goes in EncryptedSharedPreferences backed by the Android Keystore.
 */
object TokenStore {
    private const val PREFS_NAME = "ngs_secure_prefs"
    private const val KEY_TOKEN = "auth_token"

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun save(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
        ApiClient.authToken = token
    }

    fun load(context: Context): String? {
        val token = prefs(context).getString(KEY_TOKEN, null)
        ApiClient.authToken = token
        return token
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).apply()
        ApiClient.authToken = null
    }
}
