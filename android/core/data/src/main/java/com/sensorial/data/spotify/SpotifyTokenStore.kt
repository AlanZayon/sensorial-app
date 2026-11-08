package com.sensorial.data.spotify

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class SpotifyTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long,
) {
    fun isExpired(nowEpochMs: Long = System.currentTimeMillis(), skewMs: Long = 60_000L): Boolean =
        nowEpochMs >= (expiresAtEpochMs - skewMs)
}

class SpotifyTokenStore(
    private val prefs: SharedPreferences,
) {
    fun read(): SpotifyTokens? {
        migrateScopesIfNeeded()
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val expires = prefs.getLong(KEY_EXPIRES, 0L)
        if (access.isBlank() || expires <= 0L) return null
        return SpotifyTokens(
            accessToken = access,
            refreshToken = prefs.getString(KEY_REFRESH, null),
            expiresAtEpochMs = expires,
        )
    }

    fun write(tokens: SpotifyTokens) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putLong(KEY_EXPIRES, tokens.expiresAtEpochMs)
            .putInt(KEY_SCOPE_VERSION, SpotifyConfig.SCOPE_VERSION)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun migrateScopesIfNeeded() {
        val stored = prefs.getInt(KEY_SCOPE_VERSION, 0)
        if (stored < SpotifyConfig.SCOPE_VERSION && prefs.contains(KEY_ACCESS)) {
            prefs.edit().clear().apply()
        }
    }

    /** True when a Web API token exists (after scope migration check). */
    fun hasToken(): Boolean = read() != null

    companion object {
        private const val PREFS = "spotify_tokens"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_EXPIRES = "expires"
        private const val KEY_SCOPE_VERSION = "scope_version"

        fun create(context: Context): SpotifyTokenStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return SpotifyTokenStore(prefs)
        }
    }
}
