package com.sensorial.app

import android.app.Application
import com.sensorial.data.di.SensorialContainer
import com.sensorial.data.spotify.SpotifyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class SensorialApplication : Application() {
    lateinit var container: SensorialContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = runBlocking(Dispatchers.IO) {
            SensorialContainer.create(
                context = this@SensorialApplication,
                options = SensorialContainer.Options(
                    spotifyConfig = SpotifyConfig(
                        clientId = BuildConfig.SPOTIFY_CLIENT_ID,
                        redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
                    ),
                    forceMock = BuildConfig.SPOTIFY_USE_MOCK,
                ),
            )
        }
    }
}
