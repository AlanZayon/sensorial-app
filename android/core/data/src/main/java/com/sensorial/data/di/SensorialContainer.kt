package com.sensorial.data.di

import android.content.Context
import com.sensorial.data.mock.MockMusicProvider
import com.sensorial.data.persistence.RoomAppSettingsRepository
import com.sensorial.data.persistence.RoomInteractionRepository
import com.sensorial.data.persistence.RoomPlaybackHistoryRepository
import com.sensorial.data.persistence.RoomUserPreferenceRepository
import com.sensorial.data.persistence.SensorialDatabase
import com.sensorial.data.spotify.CandidateQueryBuilder
import com.sensorial.data.spotify.HybridPlaybackController
import com.sensorial.data.spotify.SpotifyAppRemoteAuth
import com.sensorial.data.spotify.SpotifyAuthRepository
import com.sensorial.data.spotify.SpotifyConfig
import com.sensorial.data.spotify.SpotifyMusicProvider
import com.sensorial.data.spotify.SpotifyTokenStore
import com.sensorial.data.spotify.SpotifyWebApi
import com.sensorial.domain.gesture.GestureInterpreter
import com.sensorial.domain.history.HistoryConfig
import com.sensorial.domain.provider.MusicProvider
import com.sensorial.domain.recommendation.ScoredHeuristicRecommendationEngine
import com.sensorial.domain.repository.AppSettingsRepository
import com.sensorial.domain.repository.InteractionRepository
import com.sensorial.domain.repository.PlaybackHistoryRepository
import com.sensorial.domain.repository.UserPreferenceRepository
import com.sensorial.domain.usecase.DiscoverySession

/**
 * Composition root. Uses Room-backed repositories hydrated before session creation.
 */
class SensorialContainer private constructor(
    val musicProvider: MusicProvider,
    val playbackHistoryRepository: PlaybackHistoryRepository,
    val interactionRepository: InteractionRepository,
    val preferenceRepository: UserPreferenceRepository,
    val settingsRepository: AppSettingsRepository,
    val recommendationEngine: ScoredHeuristicRecommendationEngine,
    val gestureInterpreter: GestureInterpreter,
    val spotifyAuth: SpotifyAuthRepository?,
    val usesSpotify: Boolean,
    val spotifyMusicProvider: SpotifyMusicProvider? = null,
    val spotifyAppRemoteAuth: SpotifyAppRemoteAuth? = null,
) {
    fun createDiscoverySession(): DiscoverySession = DiscoverySession(
        musicProvider = musicProvider,
        historyRepository = playbackHistoryRepository,
        interactionRepository = interactionRepository,
        preferenceRepository = preferenceRepository,
        settingsRepository = settingsRepository,
        recommendationEngine = recommendationEngine,
        gestureInterpreter = gestureInterpreter,
    )

    data class Options(
        val spotifyConfig: SpotifyConfig = SpotifyConfig(clientId = ""),
        val forceMock: Boolean = false,
    )

    companion object {
        suspend fun create(
            context: Context,
            options: Options = Options(),
            database: SensorialDatabase = SensorialDatabase.create(context),
        ): SensorialContainer {
            val settingsRepository = RoomAppSettingsRepository(database)
            val preferenceRepository = RoomUserPreferenceRepository(database)
            val interactionRepository = RoomInteractionRepository(database)
            settingsRepository.hydrate()
            preferenceRepository.hydrate()
            interactionRepository.hydrate()

            val historyConfig = settingsRepository.get().historyConfig
            val historyRepository = RoomPlaybackHistoryRepository(
                database = database,
                historyConfig = HistoryConfig(maxSize = historyConfig.maxSize),
            )
            historyRepository.hydrate()

            val useSpotify = !options.forceMock && options.spotifyConfig.isConfigured
            val auth: SpotifyAuthRepository?
            val musicProvider: MusicProvider
            val spotifyProvider: SpotifyMusicProvider?
            val appRemoteAuth: SpotifyAppRemoteAuth?
            if (useSpotify) {
                val tokenStore = SpotifyTokenStore.create(context)
                auth = SpotifyAuthRepository(context, options.spotifyConfig, tokenStore)
                appRemoteAuth = SpotifyAppRemoteAuth(context, options.spotifyConfig)
                // Web token was wiped (scope bump) → force App Remote re-consent too.
                if (!tokenStore.hasToken()) {
                    appRemoteAuth.clear()
                }
                val api = SpotifyWebApi(auth)
                val playback = HybridPlaybackController(context, options.spotifyConfig)
                spotifyProvider = SpotifyMusicProvider(
                    api = api,
                    candidates = CandidateQueryBuilder(api),
                    playback = playback,
                )
                musicProvider = spotifyProvider
            } else {
                auth = null
                spotifyProvider = null
                appRemoteAuth = null
                musicProvider = MockMusicProvider()
            }

            return SensorialContainer(
                musicProvider = musicProvider,
                playbackHistoryRepository = historyRepository,
                interactionRepository = interactionRepository,
                preferenceRepository = preferenceRepository,
                settingsRepository = settingsRepository,
                recommendationEngine = ScoredHeuristicRecommendationEngine(
                    weights = settingsRepository.get().recommendationWeights,
                    useModePresets = true,
                ),
                gestureInterpreter = GestureInterpreter(),
                spotifyAuth = auth,
                usesSpotify = useSpotify,
                spotifyMusicProvider = spotifyProvider,
                spotifyAppRemoteAuth = appRemoteAuth,
            )
        }

        /** Test helper that hydrates an in-memory database. */
        suspend fun createInMemory(context: Context): SensorialContainer =
            create(context, Options(forceMock = true), SensorialDatabase.createInMemory(context))
    }
}
