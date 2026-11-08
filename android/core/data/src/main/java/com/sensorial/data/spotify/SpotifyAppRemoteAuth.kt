package com.sensorial.data.spotify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

/**
 * Authorizes App Remote via Spotify Auth SDK using a **dedicated** redirect URI
 * ([SpotifyConfig.APP_REMOTE_REDIRECT_URI]) so AppAuth cannot intercept the callback.
 */
class SpotifyAppRemoteAuth(
    context: Context,
    private val config: SpotifyConfig,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        // Invalidate stale "authorized" flags from failed showAuthView-only attempts.
        if (prefs.getInt(KEY_VERSION, 0) < AUTH_VERSION) {
            prefs.edit().clear().putInt(KEY_VERSION, AUTH_VERSION).apply()
        }
    }

    fun isAuthorized(): Boolean = prefs.getBoolean(KEY_OK, false)

    fun markAuthorized() {
        prefs.edit().putBoolean(KEY_OK, true).putInt(KEY_VERSION, AUTH_VERSION).apply()
    }

    fun clear() {
        prefs.edit().clear().putInt(KEY_VERSION, AUTH_VERSION).apply()
    }

    fun openLogin(activity: Activity, requestCode: Int = REQUEST_CODE) {
        val request = AuthorizationRequest.Builder(
            config.clientId,
            AuthorizationResponse.Type.TOKEN,
            config.appRemoteRedirectUri,
        )
            .setScopes(arrayOf("app-remote-control"))
            .setShowDialog(true)
            .build()
        AuthorizationClient.openLoginActivity(activity, requestCode, request)
    }

    fun handleResult(resultCode: Int, data: Intent?): Result<Unit> {
        val response = AuthorizationClient.getResponse(resultCode, data)
        return when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                markAuthorized()
                Result.success(Unit)
            }
            AuthorizationResponse.Type.ERROR -> {
                Result.failure(
                    IllegalStateException(
                        response.error ?: "Erro no login Spotify Player",
                    ),
                )
            }
            AuthorizationResponse.Type.EMPTY -> {
                Result.failure(IllegalStateException("Autorização cancelada"))
            }
            else -> Result.failure(IllegalStateException("Resposta inesperada: ${response.type}"))
        }
    }

    companion object {
        const val REQUEST_CODE = 0x51A1
        private const val PREFS = "spotify_app_remote_auth"
        private const val KEY_OK = "authorized"
        private const val KEY_VERSION = "version"
        private const val AUTH_VERSION = 3
    }
}
