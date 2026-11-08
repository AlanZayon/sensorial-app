package com.sensorial.data.persistence

import com.sensorial.data.persistence.entity.HistoryMetaEntity
import com.sensorial.data.persistence.entity.HistoryTrackEntity
import com.sensorial.data.persistence.entity.InteractionEntity
import com.sensorial.data.persistence.entity.PreferenceEntity
import com.sensorial.data.persistence.entity.SettingsEntity
import com.sensorial.data.persistence.mapper.decodePreferenceJson
import com.sensorial.data.persistence.mapper.decodeSettingsJson
import com.sensorial.data.persistence.mapper.toDomain
import com.sensorial.data.persistence.mapper.toEntityFields
import com.sensorial.data.persistence.mapper.decodeHistoryEntryJson
import com.sensorial.data.persistence.mapper.encodeJson
import com.sensorial.domain.history.HistoryConfig
import com.sensorial.domain.history.HistoryEntry
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomPlaybackHistoryRepository(
    private val database: SensorialDatabase,
    historyConfig: HistoryConfig = HistoryConfig(),
) : PlaybackHistoryRepository {
    private val mutex = Mutex()
    private var stack = PlaybackHistory(historyConfig)
    private var maxSize: Int = historyConfig.maxSize
    private val _history = MutableStateFlow(stack.toSnapshot())
    override val history: StateFlow<PlaybackHistorySnapshot> = _history.asStateFlow()

    suspend fun hydrate() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val meta = database.historyDao().getMeta()
            val entities = database.historyDao().getTracks()
            maxSize = meta?.maxSize ?: HistoryConfig.DEFAULT_MAX_SIZE
            stack = PlaybackHistory(HistoryConfig(maxSize = maxSize))
            entities.sortedBy { it.position }.forEach { entity ->
                stack.push(decodeHistoryEntryJson(entity.trackJson))
            }
            val targetIndex = meta?.currentIndex ?: stack.currentIndex()
            while (stack.canGoBack && stack.currentIndex() > targetIndex) {
                stack.previous()
            }
            while (stack.canGoForward && stack.currentIndex() < targetIndex) {
                stack.next()
            }
            emit()
        }
    }

    override suspend fun push(
        track: Track,
        discoveryMode: DiscoveryMode,
        recommendedAtEpochMs: Long,
    ) = mutex.withLock {
        stack.push(track, discoveryMode, recommendedAtEpochMs)
        emit()
        persistLocked()
    }

    override suspend fun updateCurrentListeningProgress(progress: Double) = mutex.withLock {
        stack.updateCurrentListeningProgress(progress)
        emit()
        persistLocked()
    }

    override suspend fun previous(): Track = mutex.withLock {
        stack.previous().also {
            emit()
            persistLocked()
        }
    }

    override suspend fun next(): Track = mutex.withLock {
        stack.next().also {
            emit()
            persistLocked()
        }
    }

    override suspend fun current(): Track? = mutex.withLock { stack.currentTrack }

    override suspend fun canGoBack(): Boolean = mutex.withLock { stack.canGoBack }

    override suspend fun canGoForward(): Boolean = mutex.withLock { stack.canGoForward }

    override suspend fun reset(seed: Track?) = mutex.withLock {
        stack = PlaybackHistory(HistoryConfig(maxSize = maxSize))
        if (seed != null) stack.push(seed)
        emit()
        persistLocked()
    }

    private fun emit() {
        _history.value = stack.toSnapshot()
    }

    private suspend fun persistLocked() {
        val snapshot = stack.toSnapshot()
        val tracks = snapshot.entries.ifEmpty {
            snapshot.tracks.map { HistoryEntry(it) }
        }.mapIndexed { index, entry ->
            HistoryTrackEntity(position = index, trackJson = entry.encodeJson())
        }
        val meta = HistoryMetaEntity(
            currentIndex = snapshot.currentIndex.coerceAtLeast(-1),
            maxSize = maxSize,
        )
        withContext(Dispatchers.IO) {
            database.historyDao().replaceHistory(meta, tracks)
        }
    }
}

class RoomInteractionRepository(
    private val database: SensorialDatabase,
) : InteractionRepository {
    private val mutex = Mutex()
    private val _interactions = MutableStateFlow<List<TrackInteraction>>(emptyList())
    override val interactions: Flow<List<TrackInteraction>> = _interactions.asStateFlow()

    suspend fun hydrate() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val items = database.interactionDao().getAll().map { it.toDomain() }
            _interactions.value = items
        }
    }

    override suspend fun append(interaction: TrackInteraction) = mutex.withLock {
        val fields = interaction.toEntityFields()
        val entity = InteractionEntity(
            id = fields.id,
            trackId = fields.trackId,
            interactionType = fields.interactionType,
            verticalIntent = fields.verticalIntent,
            horizontalIntent = fields.horizontalIntent,
            gestureIntensity = fields.gestureIntensity,
            gestureVelocity = fields.gestureVelocity,
            listeningDurationMs = fields.listeningDurationMs,
            timestampEpochMs = fields.timestampEpochMs,
            artistId = fields.artistId,
            trackDurationMs = fields.trackDurationMs,
            listeningProgress = fields.listeningProgress,
            discoveryMode = fields.discoveryMode,
            localHour = fields.localHour,
            dayOfWeek = fields.dayOfWeek,
            navigationAction = fields.navigationAction,
        )
        withContext(Dispatchers.IO) {
            database.interactionDao().insert(entity)
        }
        _interactions.value = _interactions.value + interaction
    }

    override suspend fun recent(limit: Int): List<TrackInteraction> = mutex.withLock {
        withContext(Dispatchers.IO) {
            database.interactionDao().getRecentDesc(limit).asReversed().map { it.toDomain() }
        }
    }

    override suspend fun all(): List<TrackInteraction> = mutex.withLock {
        _interactions.value
    }

    override suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { database.interactionDao().clear() }
        _interactions.value = emptyList()
    }

    private fun InteractionEntity.toDomain(): TrackInteraction =
        com.sensorial.data.persistence.mapper.InteractionFields(
            id = id,
            trackId = trackId,
            interactionType = interactionType,
            verticalIntent = verticalIntent,
            horizontalIntent = horizontalIntent,
            gestureIntensity = gestureIntensity,
            gestureVelocity = gestureVelocity,
            listeningDurationMs = listeningDurationMs,
            timestampEpochMs = timestampEpochMs,
            artistId = artistId,
            trackDurationMs = trackDurationMs,
            listeningProgress = listeningProgress,
            discoveryMode = discoveryMode,
            localHour = localHour,
            dayOfWeek = dayOfWeek,
            navigationAction = navigationAction,
        ).toDomain()
}

class RoomUserPreferenceRepository(
    private val database: SensorialDatabase,
) : UserPreferenceRepository {
    private val mutex = Mutex()
    private val _profile = MutableStateFlow(UserPreferenceProfile())
    override val profile: StateFlow<UserPreferenceProfile> = _profile.asStateFlow()

    suspend fun hydrate() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entity = database.preferenceDao().get()
            _profile.value = entity?.let { decodePreferenceJson(it.profileJson) } ?: UserPreferenceProfile()
        }
    }

    override suspend fun get(): UserPreferenceProfile = mutex.withLock { _profile.value }

    override suspend fun save(profile: UserPreferenceProfile) = mutex.withLock {
        _profile.value = profile
        withContext(Dispatchers.IO) {
            database.preferenceDao().upsert(PreferenceEntity(profileJson = profile.encodeJson()))
        }
    }

    override suspend fun clear() = mutex.withLock {
        _profile.value = UserPreferenceProfile()
        withContext(Dispatchers.IO) { database.preferenceDao().clear() }
    }
}

class RoomAppSettingsRepository(
    private val database: SensorialDatabase,
) : AppSettingsRepository {
    private val mutex = Mutex()
    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    suspend fun hydrate() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entity = database.settingsDao().get()
            _settings.value = entity?.let { decodeSettingsJson(it.settingsJson) } ?: AppSettings()
        }
    }

    override suspend fun get(): AppSettings = mutex.withLock { _settings.value }

    override suspend fun save(settings: AppSettings) = mutex.withLock {
        _settings.value = settings
        withContext(Dispatchers.IO) {
            database.settingsDao().upsert(SettingsEntity(settingsJson = settings.encodeJson()))
        }
    }
}
