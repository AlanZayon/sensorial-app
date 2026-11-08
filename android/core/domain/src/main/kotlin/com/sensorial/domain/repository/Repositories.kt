package com.sensorial.domain.repository

import com.sensorial.domain.history.HistoryConfig
import com.sensorial.domain.history.HistoryEntry
import com.sensorial.domain.history.PlaybackHistory
import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackInteraction
import com.sensorial.domain.preference.UserPreferenceProfile
import com.sensorial.domain.recommendation.RecommendationWeights
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PlaybackHistoryRepository {
    val history: StateFlow<PlaybackHistorySnapshot>
    suspend fun push(
        track: Track,
        discoveryMode: DiscoveryMode = DiscoveryMode.Normal,
        recommendedAtEpochMs: Long = 0L,
    )
    suspend fun previous(): Track
    suspend fun next(): Track
    suspend fun current(): Track?
    suspend fun canGoBack(): Boolean
    suspend fun canGoForward(): Boolean
    suspend fun reset(seed: Track? = null)
    suspend fun updateCurrentListeningProgress(progress: Double) = Unit
}

data class PlaybackHistorySnapshot(
    val currentTrack: Track?,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val tracks: List<Track>,
    val currentIndex: Int,
    val entries: List<HistoryEntry> = emptyList(),
)

interface InteractionRepository {
    val interactions: Flow<List<TrackInteraction>>
    suspend fun append(interaction: TrackInteraction)
    suspend fun recent(limit: Int = 50): List<TrackInteraction>
    /** Full interaction log (hydrated / in-memory). */
    suspend fun all(): List<TrackInteraction> = recent(limit = 10_000)
    suspend fun clear()
}

interface UserPreferenceRepository {
    val profile: StateFlow<UserPreferenceProfile>
    suspend fun get(): UserPreferenceProfile
    suspend fun save(profile: UserPreferenceProfile)
    suspend fun clear()
}

data class AppSettings(
    val historyConfig: HistoryConfig = HistoryConfig(),
    val recommendationWeights: RecommendationWeights = RecommendationWeights(),
)

interface AppSettingsRepository {
    val settings: StateFlow<AppSettings>
    suspend fun get(): AppSettings
    suspend fun save(settings: AppSettings)
}

fun PlaybackHistory.toSnapshot(): PlaybackHistorySnapshot =
    PlaybackHistorySnapshot(
        currentTrack = currentTrack,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        tracks = snapshot(),
        currentIndex = currentIndex(),
        entries = entrySnapshot(),
    )
