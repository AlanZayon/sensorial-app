package com.sensorial.data.spotify

data class SpotifyConfig(
    val clientId: String,
    /** Web API PKCE (AppAuth) — must not collide with [appRemoteRedirectUri]. */
    val redirectUri: String = DEFAULT_REDIRECT_URI,
    /** App Remote / Spotify Auth LoginActivity — separate host avoids AppAuth stealing the redirect. */
    val appRemoteRedirectUri: String = APP_REMOTE_REDIRECT_URI,
    val scopes: Set<String> = DEFAULT_SCOPES,
) {
    val isConfigured: Boolean get() = clientId.isNotBlank()

    companion object {
        const val DEFAULT_REDIRECT_URI = "com.sensorial.app://callback"
        const val APP_REMOTE_REDIRECT_URI = "com.sensorial.app://app-remote"
        val DEFAULT_SCOPES = setOf(
            "app-remote-control",
            "user-read-email",
            // Required for market=from_token (preview URLs for the user's country).
            "user-read-private",
        )
        /** Bump when scopes change so stored tokens are invalidated and user re-consents. */
        const val SCOPE_VERSION = 2
        const val AUTHORIZE_ENDPOINT = "https://accounts.spotify.com/authorize"
        const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
        const val API_BASE = "https://api.spotify.com/v1"
    }
}
