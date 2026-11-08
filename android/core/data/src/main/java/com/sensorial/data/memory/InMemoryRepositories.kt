package com.sensorial.data.memory

import com.sensorial.domain.history.HistoryConfig
import com.sensorial.domain.history.PlaybackHistory
import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackInteraction
import com.sensorial.domain.preference.UserPreferenceProfile
import com.sensorial.domain.repository.AppSettings
import com.sensorial.domain.repository.AppSettingsRepository
import com.sensorial.domain.repository.InteractionRepository
import com.sensorial.domain.repository.PlaybackHistoryRepository
import com.sensorial.domain.repository.PlaybackHistorySnapshot
import com.sensorial.domain.repository.UserPreferenceRepository
import com.sensorial.domain.repository.toSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryPlaybackHistoryRepository(
    private val historyConfig: HistoryConfig = HistoryConfig(),
) : PlaybackHistoryRepository {
    private val mutex = Mutex()
    private var stack = PlaybackHistory(historyConfig)
    private val _history = MutableStateFlow(stack.toSnapshot())
    override val history: StateFlow<PlaybackHistorySnapshot> = _history.asStateFlow()

    override suspend fun push(
        track: Track,
        discoveryMode: DiscoveryMode,
        recommendedAtEpochMs: Long,
    ) = mutex.withLock {
        stack.push(track, discoveryMode, recommendedAtEpochMs)
        emit()
    }

    override suspend fun updateCurrentListeningProgress(progress: Double) = mutex.withLock {
        stack.updateCurrentListeningProgress(progress)
        emit()
    }

    override suspend fun previous(): Track = mutex.withLock {
        stack.previous().also { emit() }
    }

    override suspend fun next(): Track = mutex.withLock {
        stack.next().also { emit() }
    }

    override suspend fun current(): Track? = mutex.withLock { stack.currentTrack }

    override suspend fun canGoBack(): Boolean = mutex.withLock { stack.canGoBack }

    override suspend fun canGoForward(): Boolean = mutex.withLock { stack.canGoForward }

    override suspend fun reset(seed: Track?) = mutex.withLock {
        stack = PlaybackHistory(historyConfig)
        if (seed != null) stack.push(seed)
        emit()
    }

    private fun emit() {
        _history.value = stack.toSnapshot()
    }
}

class InMemoryInteractionRepository : InteractionRepository {
    private val mutex = Mutex()
    private val items = mutableListOf<TrackInteraction>()
    private val _interactions = MutableStateFlow<List<TrackInteraction>>(emptyList())
    override val interactions: Flow<List<TrackInteraction>> = _interactions.asStateFlow()

    override suspend fun append(interaction: TrackInteraction) = mutex.withLock {
        items += interaction
        _interactions.value = items.toList()
    }

    override suspend fun recent(limit: Int): List<TrackInteraction> = mutex.withLock {
        items.takeLast(limit)
    }

    override suspend fun clear() = mutex.withLock {
        items.clear()
        _interactions.value = emptyList()
    }
}

class InMemoryUserPreferenceRepository : UserPreferenceRepository {
    private val mutex = Mutex()
    private val _profile = MutableStateFlow(UserPreferenceProfile())
    override val profile: StateFlow<UserPreferenceProfile> = _profile.asStateFlow()

    override suspend fun get(): UserPreferenceProfile = mutex.withLock { _profile.value }

    override suspend fun save(profile: UserPreferenceProfile) = mutex.withLock {
        _profile.value = profile
    }

    override suspend fun clear() = mutex.withLock {
        _profile.value = UserPreferenceProfile()
    }
}

class InMemoryAppSettingsRepository(
    initial: AppSettings = AppSettings(),
) : AppSettingsRepository {
    private val mutex = Mutex()
    private val _settings = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override suspend fun get(): AppSettings = mutex.withLock { _settings.value }

    override suspend fun save(settings: AppSettings) = mutex.withLock {
        _settings.value = settings
    }
}
