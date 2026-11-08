package com.sensorial.data.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Authorization Code + PKCE against Spotify Accounts.
 * UI launches [createAuthIntent]; ActivityResult forwards the Intent to [exchangeAuthorization].
 */
class SpotifyAuthRepository(
    context: Context,
    private val config: SpotifyConfig,
    private val tokenStore: SpotifyTokenStore,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val appContext = context.applicationContext
    private val authService = AuthorizationService(appContext)
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(SpotifyConfig.AUTHORIZE_ENDPOINT),
        Uri.parse(SpotifyConfig.TOKEN_ENDPOINT),
    )

    private val _authorized = MutableStateFlow(tokenStore.read() != null)
    val authorized: StateFlow<Boolean> = _authorized.asStateFlow()

    fun isAuthorized(): Boolean = tokenStore.read() != null

    fun createAuthIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            config.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(config.redirectUri),
        )
            .setScopes(*config.scopes.toTypedArray())
            // Force consent UI so newly added scopes (e.g. user-read-private) are granted.
            .setAdditionalParameters(mapOf("show_dialog" to "true"))
            .build()
        return authService.getAuthorizationRequestIntent(request)
    }

    suspend fun exchangeAuthorization(intent: Intent): Boolean = withContext(Dispatchers.IO) {
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        if (response == null) {
            throw ex ?: IllegalStateException("Spotify authorization failed")
        }
        val tokens = performTokenRequest(response.createTokenExchangeRequest())
        tokenStore.write(tokens)
        _authorized.value = true
        true
    }

    suspend fun validAccessToken(): String {
        val current = tokenStore.read() ?: error("Not authorized with Spotify")
        if (!current.isExpired()) return current.accessToken
        val refresh = current.refreshToken ?: error("Spotify session expired; reconnect")
        val refreshed = refreshAccessToken(refresh)
        tokenStore.write(refreshed)
        return refreshed.accessToken
    }

    fun signOut() {
        tokenStore.clear()
        _authorized.value = false
    }

    fun dispose() {
        authService.dispose()
    }

    private suspend fun performTokenRequest(request: TokenRequest): SpotifyTokens =
        suspendCoroutine { cont ->
            authService.performTokenRequest(request) { response, ex ->
                if (response != null) {
                    cont.resume(
                        SpotifyTokens(
                            accessToken = checkNotNull(response.accessToken),
                            refreshToken = response.refreshToken,
                            expiresAtEpochMs = response.accessTokenExpirationTime
                                ?: (System.currentTimeMillis() + 3_600_000L),
                        ),
                    )
                } else {
                    cont.resumeWithException(ex ?: IllegalStateException("Token exchange failed"))
                }
            }
        }

    private fun refreshAccessToken(refreshToken: String): SpotifyTokens {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", config.clientId)
            .build()
        val request = Request.Builder()
            .url(SpotifyConfig.TOKEN_ENDPOINT)
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Spotify token refresh failed: HTTP ${response.code}")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val expiresIn = json.optLong("expires_in", 3600L)
            val newRefresh = json.optString("refresh_token", "").ifBlank { null }
            return SpotifyTokens(
                accessToken = json.getString("access_token"),
                refreshToken = newRefresh ?: refreshToken,
                expiresAtEpochMs = System.currentTimeMillis() + expiresIn * 1000L,
            )
        }
    }
}
