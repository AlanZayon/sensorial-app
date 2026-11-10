package com.sensorial.domain.usecase

import com.sensorial.domain.feed.FeedState
import com.sensorial.domain.feed.PlaybackStatus
import com.sensorial.domain.gesture.GestureInterpreter
import com.sensorial.domain.gesture.RawGestureInput
import com.sensorial.domain.gesture.UserIntent
import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.GestureIntensity
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.NavigationAction
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction
import com.sensorial.domain.preference.LikedTracks
import com.sensorial.domain.provider.MusicProvider
import com.sensorial.domain.preference.QuickSkip
import com.sensorial.domain.recommendation.ContextualWeightPolicy
import com.sensorial.domain.recommendation.DiscoveryExclusions
import com.sensorial.domain.recommendation.RecommendationContext
import com.sensorial.domain.recommendation.RecommendationEngine
import com.sensorial.domain.recommendation.SessionAffinity
import com.sensorial.domain.recommendation.WeightArm
import com.sensorial.domain.repository.AppSettingsRepository
import com.sensorial.domain.repository.InteractionRepository
import com.sensorial.domain.repository.PlaybackHistoryRepository
import com.sensorial.domain.repository.UserPreferenceRepository
import com.sensorial.domain.util.Clock
import com.sensorial.domain.util.IdGenerator
import com.sensorial.domain.util.SystemClock
import com.sensorial.domain.util.UuidIdGenerator
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/**
 * Coordinates discovery session state. Serializes rapid gestures with a mutex.
 *
 * Navigation, discovery strategy, and explicit preference are separate paths.
 */
class DiscoverySession(
    private val musicProvider: MusicProvider,
    private val historyRepository: PlaybackHistoryRepository,
    private val interactionRepository: InteractionRepository,
    private val preferenceRepository: UserPreferenceRepository,
    private val settingsRepository: AppSettingsRepository,
    private val recommendationEngine: RecommendationEngine,
    private val gestureInterpreter: GestureInterpreter = GestureInterpreter(),
    private val clock: Clock = SystemClock,
    private val idGenerator: IdGenerator = UuidIdGenerator,
    private val weightPolicy: ContextualWeightPolicy = ContextualWeightPolicy(),
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private var listeningStartedAtMs: Long = clock.nowEpochMs()
    private var prefetchedCandidates: List<Track> = emptyList()
    private var prefetchedForMode: DiscoveryMode = DiscoveryMode.Normal
    private var lastRelatedArtistIds: Set<com.sensorial.domain.model.ArtistId> = emptySet()
    private var pendingPlayTrackId: TrackId? = null
    private val likedTrackIds = mutableSetOf<TrackId>()
    private var sessionAffinity: SessionAffinity = SessionAffinity.EMPTY
    private var pendingWeightArm: WeightArm? = null
    /** Active liked-playlist queue; when non-empty, advance/end stay inside it. */
    private var libraryQueue: List<Track> = emptyList()
    private var libraryQueueIndex: Int = -1

    suspend fun start() {
        _state.update { it.copy(status = PlaybackStatus.Loading, errorMessage = null) }
        try {
            hydrateLikedTrackIds()
            val existing = historyRepository.current()
            if (existing != null) {
                val resumed = runCatching {
                    mutex.withLock {
                        commitTrackLocked(existing, lastIntentLabel = null, alreadyInHistory = true)
                    }
                    playCommitted(existing)
                    true
                }.getOrDefault(false)
                if (resumed) {
                    // playCommitted already ran above; only warm the next pool.
                    warmPrefetch(existing)
                    return
                }
            }

            val profile = preferenceRepository.get()
            val seedGenres = profile.topPositiveGenres()
            val exclusion = discoveryExclusion()
            val pool = musicProvider.getCandidatePool(
                RecommendationContext(
                    currentTrack = null,
                    recentInteractions = emptyList(),
                    excludedIds = exclusion.ids,
                    seedGenres = seedGenres,
                    topArtists = profile.topPositiveArtists(),
                    discoveryMode = DiscoveryMode.Normal,
                    localHour = localHour(),
                    dayOfWeek = dayOfWeek(),
                    sessionAffinity = sessionAffinity,
                    blockedFingerprints = exclusion.fingerprints,
                ),
            )
            lastRelatedArtistIds = pool.relatedArtistIds
            val candidates = pool.tracks
            if (candidates.isEmpty()) {
                _state.update {
                    it.copy(status = PlaybackStatus.NoRecommendations, currentTrack = null)
                }
                return
            }

            var lastError: Exception? = null
            for (seed in candidates) {
                try {
                    mutex.withLock {
                        historyRepository.reset(seed)
                        commitTrackLocked(seed, lastIntentLabel = null, alreadyInHistory = true)
                    }
                    playAndWarm(seed)
                    return
                } catch (error: Exception) {
                    lastError = error
                    mutex.withLock {
                        if (pendingPlayTrackId == seed.id) pendingPlayTrackId = null
                    }
                }
            }
            throw lastError ?: IllegalStateException("Nenhuma faixa reproduzível encontrada")
        } catch (error: Exception) {
            _state.update {
                it.copy(status = PlaybackStatus.Error, errorMessage = error.message ?: "Failed to start")
            }
        }
    }

    suspend fun goToPrevious() = goBack()

    suspend fun goToNextInHistory() {
        val track = mutex.withLock {
            if (!historyRepository.canGoForward()) return
            _state.update { it.copy(status = PlaybackStatus.ChangingTrack) }
            closeListeningLocked(NavigationAction.Advance, DiscoveryMode.Normal, InteractionType.NavigationAdvance)
            val next = historyRepository.next()
            commitTrackLocked(next, lastIntentLabel = "Próxima", alreadyInHistory = true)
            next
        }
        playAndWarm(track)
    }

    /** ↓ — previous in liked queue, else previous in the single timeline. */
    suspend fun goBack() {
        if (libraryQueue.isNotEmpty() && libraryQueueIndex > 0) {
            playLibraryRelative(-1)
            return
        }
        val track = mutex.withLock {
            clearLibraryQueueLocked()
            if (!historyRepository.canGoBack()) return
            _state.update { it.copy(status = PlaybackStatus.ChangingTrack) }
            closeListeningLocked(NavigationAction.Return, DiscoveryMode.Normal, InteractionType.NavigationReturn)
            val previous = historyRepository.previous()
            commitTrackLocked(previous, lastIntentLabel = "Anterior", alreadyInHistory = true)
            previous
        }
        playAndWarm(track)
    }

    /**
     * ↑ — next in liked queue, history, or Normal recommendation when already at the tip.
     */
    suspend fun advance() {
        if (libraryQueue.isNotEmpty()) {
            playLibraryRelative(+1)
            return
        }
        if (historyRepository.canGoForward()) {
            goToNextInHistory()
            return
        }
        discover(DiscoveryMode.Normal, intentLabel = "Próxima")
    }

    /** ← / → discovery modes — always append a new recommendation to the timeline. */
    suspend fun discover(mode: DiscoveryMode, intentLabel: String? = null) {
        val label = intentLabel ?: when (mode) {
            DiscoveryMode.Normal -> "Próxima"
            DiscoveryMode.Deepen -> "Aprofundar"
            DiscoveryMode.Explore -> "Explorar"
        }
        val prefetchTrack = mutex.withLock {
            // Leaving discovery gestures exits the liked playlist queue.
            clearLibraryQueueLocked()
            val current = historyRepository.current() ?: return
            _state.update {
                it.copy(status = PlaybackStatus.ChangingTrack, lastIntentLabel = label)
            }
            closeListeningLocked(
                navigationAction = if (mode == DiscoveryMode.Normal) {
                    NavigationAction.Advance
                } else {
                    NavigationAction.None
                },
                discoveryMode = mode,
                interactionType = if (mode == DiscoveryMode.Normal) {
                    InteractionType.NavigationAdvance
                } else {
                    InteractionType.Discovery
                },
            )

            val next = pickNextLocked(current, mode)
            if (next == null) {
                _state.update {
                    val snapshot = historyRepository.history.value
                    it.copy(
                        status = PlaybackStatus.NoRecommendations,
                        currentTrack = current,
                        canGoBack = snapshot.canGoBack,
                        canGoForward = snapshot.canGoForward,
                    )
                }
                return@withLock null
            }
            historyRepository.push(next, mode, clock.nowEpochMs())
            commitTrackLocked(next, lastIntentLabel = label, alreadyInHistory = true)
            next
        }
        prefetchTrack?.let { track -> playAndWarm(track, mode) }
    }

    /** Liked ids newest-first (cheap; no network). */
    suspend fun likedTrackRefs() = LikedTracks.current(interactionRepository.all())

    /** Locally cached track metadata for liked ids (history + current). */
    fun likedTracksLocalCache(): Map<TrackId, Track> {
        val snapshot = historyRepository.history.value
        val map = LinkedHashMap<TrackId, Track>()
        snapshot.tracks.forEach { map[it.id] = it }
        snapshot.currentTrack?.let { map[it.id] = it }
        _state.value.currentTrack?.let { map[it.id] = it }
        return map
    }

    /** Batch-resolve track metadata for liked ids (network). */
    suspend fun resolveLikedTracks(ids: List<TrackId>): Map<TrackId, Track> {
        if (ids.isEmpty()) return emptyMap()
        val local = likedTracksLocalCache()
        val missing = ids.filter { it !in local }
        val remote = if (missing.isEmpty()) emptyMap() else musicProvider.getTracks(missing)
        return buildMap {
            ids.forEach { id ->
                (local[id] ?: remote[id])?.let { put(id, it) }
            }
        }
    }

    /**
     * Current liked tracks (newest first), resolved from history cache and Spotify.
     */
    suspend fun likedTracks(): List<Track> {
        val refs = likedTrackRefs()
        if (refs.isEmpty()) return emptyList()
        val resolved = resolveLikedTracks(refs.map { it.trackId })
        return refs.mapNotNull { resolved[it.trackId] }
    }

    /**
     * Play a liked track and bind [queue] as the only advance order until discovery exits it.
     */
    suspend fun playFromLibrary(track: Track, queue: List<Track> = listOf(track)) {
        val ordered = queue.ifEmpty { listOf(track) }.distinctBy { it.id }
        val startIndex = ordered.indexOfFirst { it.id == track.id }.let { if (it < 0) 0 else it }
        val start = ordered.getOrNull(startIndex) ?: track
        val toPlay = mutex.withLock {
            libraryQueue = ordered
            libraryQueueIndex = startIndex
            val current = historyRepository.current()
            if (current?.id == start.id) {
                publishPlaying(lastIntentLabel = "Curtidas")
                return@withLock null
            }
            if (current != null) {
                closeListeningLocked(
                    navigationAction = NavigationAction.None,
                    discoveryMode = DiscoveryMode.Normal,
                    interactionType = InteractionType.NavigationAdvance,
                )
            }
            _state.update { it.copy(status = PlaybackStatus.ChangingTrack, lastIntentLabel = "Curtidas") }
            historyRepository.push(start, DiscoveryMode.Normal, clock.nowEpochMs())
            commitTrackLocked(start, lastIntentLabel = "Curtidas", alreadyInHistory = true)
            start
        }
        toPlay?.let { playCommitted(it) }
    }

    /**
     * Toggle explicit like on the current track — does not change playback.
     * Tapping again undoes the like and reverses its preference impact.
     */
    suspend fun explicitLike() = mutex.withLock {
        val current = historyRepository.current() ?: return
        if (current.id in likedTrackIds) {
            val interaction = buildInteraction(
                track = current,
                type = InteractionType.ExplicitUnlike,
                discoveryMode = DiscoveryMode.Normal,
                navigationAction = NavigationAction.None,
                intensity = GestureIntensity.High,
                velocity = 1.0,
            )
            interactionRepository.append(interaction)
            preferenceRepository.save(preferenceRepository.get().applying(current, interaction))
            likedTrackIds.remove(current.id)
            _state.update {
                it.copy(lastIntentLabel = "Curtida removida", isCurrentTrackLiked = false)
            }
            return
        }
        val interaction = buildInteraction(
            track = current,
            type = InteractionType.ExplicitLike,
            discoveryMode = DiscoveryMode.Normal,
            navigationAction = NavigationAction.None,
            intensity = GestureIntensity.High,
            velocity = 1.0,
        )
        interactionRepository.append(interaction)
        preferenceRepository.save(preferenceRepository.get().applying(current, interaction))
        likedTrackIds.add(current.id)
        _state.update {
            it.copy(lastIntentLabel = "Gostei", isCurrentTrackLiked = true)
        }
    }

    /** Explicit dislike — soft negative; does not change track. */
    suspend fun explicitDislike() = mutex.withLock {
        val current = historyRepository.current() ?: return
        val interaction = buildInteraction(
            track = current,
            type = InteractionType.ExplicitDislike,
            discoveryMode = DiscoveryMode.Normal,
            navigationAction = NavigationAction.None,
            intensity = GestureIntensity.High,
            velocity = 1.0,
        )
        interactionRepository.append(interaction)
        preferenceRepository.save(preferenceRepository.get().applying(current, interaction))
        _state.update { it.copy(lastIntentLabel = "Não quero") }
    }

    suspend fun togglePause() = mutex.withLock {
        val current = _state.value
        when (current.status) {
            PlaybackStatus.Playing -> {
                musicProvider.pausePlayback()
                _state.update { it.copy(status = PlaybackStatus.Paused) }
            }
            PlaybackStatus.Paused -> {
                musicProvider.resumePlayback()
                _state.update { it.copy(status = PlaybackStatus.Playing) }
            }
            else -> Unit
        }
    }

    suspend fun interpretAndHandle(input: RawGestureInput) {
        when (val intent = gestureInterpreter.interpret(input)) {
            UserIntent.NavigateBack -> goBack()
            UserIntent.NavigateForward -> advance()
            is UserIntent.Discover -> discover(intent.mode)
            UserIntent.Invalid -> Unit
        }
    }

    suspend fun syncToExternalTrack(trackId: TrackId) {
        val prefetchTrack = mutex.withLock {
            // Our own play request confirmed — drop the pending guard without rewriting UI.
            if (pendingPlayTrackId == trackId) {
                pendingPlayTrackId = null
            }
            val current = historyRepository.current()
            if (current?.id == trackId) {
                if (_state.value.status == PlaybackStatus.ChangingTrack) {
                    publishPlaying(lastIntentLabel = _state.value.lastIntentLabel)
                }
                return@withLock null
            }
            // Still waiting for a different committed play — ignore lagging PlayerState.
            if (pendingPlayTrackId != null && pendingPlayTrackId != trackId) return@withLock null
            if (_state.value.status == PlaybackStatus.ChangingTrack) return@withLock null
            // External player jump leaves the liked queue.
            if (libraryQueue.none { it.id == trackId }) {
                clearLibraryQueueLocked()
            } else {
                libraryQueueIndex = libraryQueue.indexOfFirst { it.id == trackId }
            }
            val track = musicProvider.getTrack(trackId) ?: return@withLock null
            historyRepository.push(track, DiscoveryMode.Normal, clock.nowEpochMs())
            listeningStartedAtMs = clock.nowEpochMs()
            publishPlaying(lastIntentLabel = if (libraryQueue.isNotEmpty()) "Curtidas" else null)
            track
        }
        prefetchTrack?.let { track ->
            if (libraryQueue.isEmpty()) warmPrefetch(track)
        }
    }

    suspend fun onTrackEnded() {
        if (libraryQueue.isNotEmpty()) {
            playLibraryRelative(+1)
            return
        }
        // Wait for the mutex — tryLock used to drop natural ends while a gesture held the lock,
        // leaving Spotify autoplay running against a stale feed UI.
        val prefetchTrack = mutex.withLock {
            if (pendingPlayTrackId != null) return@withLock null
            val current = historyRepository.current() ?: return@withLock null
            if (_state.value.status != PlaybackStatus.Playing &&
                _state.value.status != PlaybackStatus.Paused
            ) {
                return@withLock null
            }
            // Stale end from the previous track after a fast skip.
            if (clock.nowEpochMs() - listeningStartedAtMs < 1_500L) return@withLock null
            _state.update { it.copy(status = PlaybackStatus.ChangingTrack) }
            try {
                closeListeningLocked(
                    NavigationAction.Advance,
                    DiscoveryMode.Normal,
                    InteractionType.NavigationAdvance,
                )
                val next = pickNextLocked(current, DiscoveryMode.Normal)
                if (next == null) {
                    val snapshot = historyRepository.history.value
                    _state.update {
                        it.copy(
                            status = PlaybackStatus.NoRecommendations,
                            currentTrack = current,
                            canGoBack = snapshot.canGoBack,
                            canGoForward = snapshot.canGoForward,
                        )
                    }
                    return@withLock null
                }
                historyRepository.push(next, DiscoveryMode.Normal, clock.nowEpochMs())
                commitTrackLocked(next, lastIntentLabel = "Próxima", alreadyInHistory = true)
                next
            } catch (error: Exception) {
                _state.update {
                    it.copy(status = PlaybackStatus.Error, errorMessage = error.message ?: "Falha ao avançar")
                }
                null
            }
        }
        prefetchTrack?.let { track -> playAndWarm(track) }
    }

    private suspend fun playLibraryRelative(delta: Int) {
        val toPlay = mutex.withLock {
            if (libraryQueue.isEmpty()) return@withLock null
            val target = libraryQueueIndex + delta
            if (target !in libraryQueue.indices) {
                if (delta > 0) {
                    val current = historyRepository.current()
                    _state.update {
                        it.copy(
                            status = PlaybackStatus.NoRecommendations,
                            currentTrack = current,
                            lastIntentLabel = "Fim das curtidas",
                            libraryPlayback = true,
                            canGoBack = libraryQueueIndex > 0 || historyRepository.history.value.canGoBack,
                            canGoForward = false,
                            isCurrentTrackLiked = isLiked(current?.id),
                        )
                    }
                }
                return@withLock null
            }
            val current = historyRepository.current()
            if (current != null) {
                closeListeningLocked(
                    navigationAction = if (delta >= 0) NavigationAction.Advance else NavigationAction.Return,
                    discoveryMode = DiscoveryMode.Normal,
                    interactionType = if (delta >= 0) {
                        InteractionType.NavigationAdvance
                    } else {
                        InteractionType.NavigationReturn
                    },
                )
            }
            libraryQueueIndex = target
            val next = libraryQueue[target]
            _state.update { it.copy(status = PlaybackStatus.ChangingTrack, lastIntentLabel = "Curtidas") }
            historyRepository.push(next, DiscoveryMode.Normal, clock.nowEpochMs())
            commitTrackLocked(next, lastIntentLabel = "Curtidas", alreadyInHistory = true)
            next
        }
        toPlay?.let { playCommitted(it) }
    }

    private fun clearLibraryQueueLocked() {
        libraryQueue = emptyList()
        libraryQueueIndex = -1
    }

    suspend fun onExternalPaused(paused: Boolean) {
        if (!mutex.tryLock()) return
        try {
            val status = _state.value.status
            if (status != PlaybackStatus.Playing && status != PlaybackStatus.Paused) return
            _state.update {
                it.copy(status = if (paused) PlaybackStatus.Paused else PlaybackStatus.Playing)
            }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun closeListeningLocked(
        navigationAction: NavigationAction,
        discoveryMode: DiscoveryMode,
        interactionType: InteractionType,
    ) {
        val current = historyRepository.current() ?: return
        val interaction = buildInteraction(
            track = current,
            type = interactionType,
            discoveryMode = discoveryMode,
            navigationAction = navigationAction,
            intensity = GestureIntensity.Medium,
            velocity = 0.5,
        )
        interactionRepository.append(interaction)
        preferenceRepository.save(preferenceRepository.get().applying(current, interaction))
        sessionAffinity = sessionAffinity.applying(current, interaction)
        historyRepository.updateCurrentListeningProgress(interaction.effectiveListeningProgress)
        updateBanditLocked(current, interaction)
    }

    private suspend fun pickNextLocked(current: Track, mode: DiscoveryMode): Track? {
        val recent = interactionRepository.recent(limit = 20)
        val snapshot = historyRepository.history.value
        val exclusion = discoveryExclusion(extra = setOf(current.id))
        val excluded = exclusion.ids
        val blockedFingerprints = exclusion.fingerprints
        val profile = preferenceRepository.get()
        val seedGenres = profile.topPositiveGenres()
        val topArtists = profile.topPositiveArtists()
        val cacheUsable = prefetchedCandidates.isNotEmpty() &&
            (prefetchedForMode == mode || mode == DiscoveryMode.Normal)
        val cached = if (cacheUsable) {
            prefetchedCandidates.filter {
                it.id !in excluded &&
                    com.sensorial.domain.recommendation.TrackFingerprint.of(it) !in blockedFingerprints
            }
        } else {
            emptyList()
        }
        val fromCache = recommendFrom(
            cached, current, recent, excluded, blockedFingerprints, seedGenres, topArtists,
            profile, snapshot.tracks, mode, lastRelatedArtistIds,
        )
        if (fromCache != null) {
            // Keep remaining candidates so rapid swipes stay cache-hit while warm runs.
            prefetchedCandidates = cached.filter { it.id != fromCache.id }
            return fromCache
        }
        val pool = musicProvider.getCandidatePool(
            RecommendationContext(
                currentTrack = current,
                recentInteractions = recent,
                excludedIds = excluded,
                seedGenres = seedGenres,
                topArtists = topArtists,
                discoveryMode = mode,
                recentTracks = snapshot.tracks,
                localHour = localHour(),
                dayOfWeek = dayOfWeek(),
                sessionAffinity = sessionAffinity,
                prefetch = false,
                blockedFingerprints = blockedFingerprints,
            ),
        )
        lastRelatedArtistIds = pool.relatedArtistIds
        val picked = recommendFrom(
            pool.tracks, current, recent, excluded, blockedFingerprints, seedGenres, topArtists,
            profile, snapshot.tracks, mode, pool.relatedArtistIds,
        )
        if (picked != null) {
            prefetchedCandidates = pool.tracks.filter {
                it.id != picked.id &&
                    it.id !in excluded &&
                    com.sensorial.domain.recommendation.TrackFingerprint.of(it) !in blockedFingerprints
            }
        }
        return picked
    }

    private suspend fun recommendFrom(
        candidates: List<Track>,
        current: Track,
        recent: List<TrackInteraction>,
        excluded: Set<TrackId>,
        blockedFingerprints: Set<String>,
        seedGenres: List<com.sensorial.domain.model.GenreId>,
        topArtists: List<com.sensorial.domain.model.ArtistId>,
        profile: com.sensorial.domain.preference.UserPreferenceProfile,
        historyTracks: List<Track>,
        mode: DiscoveryMode,
        relatedArtistIds: Set<com.sensorial.domain.model.ArtistId>,
    ): Track? {
        if (candidates.isEmpty()) return null
        val interactionTracks = resolveInteractionTracks(recent, candidates, historyTracks, current)
        val settings = settingsRepository.get()
        val isCold = profile.positiveSignalCount + profile.negativeSignalCount == 0 &&
            profile.genreScores.isEmpty() && profile.artistScores.isEmpty()
        val featureConfidenceMean = candidates.map { it.featureConfidence }.average()
        val decision = weightPolicy.select(
            mode = mode,
            state = settings.banditState,
            isColdStart = isCold,
            featureConfidenceMean = featureConfidenceMean,
        )
        pendingWeightArm = decision.arm
        val context = RecommendationContext(
            currentTrack = current,
            recentInteractions = recent,
            excludedIds = excluded,
            interactionTracks = interactionTracks,
            seedGenres = seedGenres,
            topArtists = topArtists,
            relatedArtistIds = relatedArtistIds,
            discoveryMode = mode,
            recentTracks = historyTracks,
            localHour = localHour(),
            dayOfWeek = dayOfWeek(),
            sessionAffinity = sessionAffinity,
            blockedFingerprints = blockedFingerprints,
            weightOverride = decision.weights,
        )
        return recommendationEngine.recommend(profile, context, candidates)
    }

    private suspend fun updateBanditLocked(track: Track, interaction: TrackInteraction) {
        val arm = pendingWeightArm ?: return
        val wasQuickSkip = QuickSkip.isQuickSkip(interaction, track.durationMs)
        val reward = weightPolicy.reward(interaction, wasQuickSkip)
        val settings = settingsRepository.get()
        val updated = weightPolicy.update(settings.banditState, arm, reward)
        settingsRepository.save(settings.copy(banditState = updated))
        pendingWeightArm = null
    }

    private suspend fun discoveryExclusion(
        extra: Set<TrackId> = emptySet(),
    ): ExclusionBundle {
        val interactions = interactionRepository.all()
        val entries = historyRepository.history.value.entries
        val ids = DiscoveryExclusions.ids(
            likedIds = likedTrackIds.toSet(),
            interactions = interactions,
            historyEntries = entries,
            extra = extra,
        )
        val heardIds = DiscoveryExclusions.wellHeardIds(interactions, entries)
        val knownTracks = buildList {
            addAll(entries.map { it.track })
            addAll(likedTracksLocalCache().values)
            _state.value.currentTrack?.let { add(it) }
        }
        val fingerprints = DiscoveryExclusions.fingerprints(
            knownTracks = knownTracks,
            likedIds = likedTrackIds.toSet(),
            heardIds = heardIds,
        )
        return ExclusionBundle(ids = ids, fingerprints = fingerprints)
    }

    private data class ExclusionBundle(
        val ids: Set<TrackId>,
        val fingerprints: Set<String>,
    )

    private suspend fun commitTrackLocked(
        track: Track,
        lastIntentLabel: String?,
        alreadyInHistory: Boolean,
    ) {
        if (!alreadyInHistory) {
            historyRepository.push(track, DiscoveryMode.Normal, clock.nowEpochMs())
        }
        pendingPlayTrackId = track.id
        listeningStartedAtMs = clock.nowEpochMs()
        // Show new art/title immediately, but keep ChangingTrack until play is issued so
        // gestures stay blocked through the Spotify handoff.
        publishFeed(PlaybackStatus.ChangingTrack, lastIntentLabel)
    }

    private suspend fun playCommitted(track: Track) {
        try {
            musicProvider.playTrack(track.id)
            mutex.withLock {
                // Only promote if this track is still the committed one.
                if (historyRepository.current()?.id != track.id) return@withLock
                if (pendingPlayTrackId == track.id) {
                    pendingPlayTrackId = null
                }
                if (_state.value.status == PlaybackStatus.ChangingTrack) {
                    publishPlaying(lastIntentLabel = _state.value.lastIntentLabel)
                }
            }
        } catch (error: Exception) {
            mutex.withLock {
                if (pendingPlayTrackId == track.id) {
                    pendingPlayTrackId = null
                }
            }
            throw error
        }
    }

    private suspend fun playAndWarm(track: Track, mode: DiscoveryMode = DiscoveryMode.Normal) = coroutineScope {
        // Await play before returning so a newer swipe cannot start Spotify before this request
        // has at least issued play (generation bump). Prefetch stays parallel.
        launch { warmPrefetch(track, mode) }
        playCommitted(track)
    }

    private suspend fun warmPrefetch(current: Track, mode: DiscoveryMode = DiscoveryMode.Normal) {
        val pool = runCatching {
            val profile = preferenceRepository.get()
            val recent = interactionRepository.recent(limit = 20)
            val exclusion = discoveryExclusion(extra = setOf(current.id))
            musicProvider.getCandidatePool(
                RecommendationContext(
                    currentTrack = current,
                    recentInteractions = recent,
                    excludedIds = exclusion.ids,
                    seedGenres = profile.topPositiveGenres(),
                    topArtists = profile.topPositiveArtists(),
                    // Mode-aware prefetch so Deepen/Explore hit compatible candidates.
                    discoveryMode = mode,
                    recentTracks = historyRepository.history.value.tracks,
                    localHour = localHour(),
                    dayOfWeek = dayOfWeek(),
                    sessionAffinity = sessionAffinity,
                    prefetch = true,
                    blockedFingerprints = exclusion.fingerprints,
                ),
            )
        }.getOrElse { return }

        mutex.withLock {
            if (historyRepository.current()?.id != current.id) return
            prefetchedCandidates = pool.tracks
            prefetchedForMode = mode
            lastRelatedArtistIds = pool.relatedArtistIds
            _state.update { it.copy(upcomingTracks = pool.tracks.take(3)) }
        }
    }

    private suspend fun hydrateLikedTrackIds() {
        likedTrackIds.clear()
        likedTrackIds.addAll(LikedTracks.current(interactionRepository.all()).map { it.trackId })
    }

    private fun isLiked(trackId: TrackId?): Boolean =
        trackId != null && trackId in likedTrackIds

    private fun publishPlaying(lastIntentLabel: String?) {
        publishFeed(PlaybackStatus.Playing, lastIntentLabel)
    }

    private fun publishFeed(status: PlaybackStatus, lastIntentLabel: String?) {
        val snapshot = historyRepository.history.value
        val inLibrary = libraryQueue.isNotEmpty()
        val libraryCanBack = inLibrary && libraryQueueIndex > 0
        val libraryCanForward = inLibrary && libraryQueueIndex < libraryQueue.lastIndex
        _state.update {
            FeedState(
                status = status,
                currentTrack = snapshot.currentTrack,
                canGoBack = libraryCanBack || snapshot.canGoBack,
                canGoForward = if (inLibrary) libraryCanForward else snapshot.canGoForward,
                upcomingTracks = if (inLibrary) {
                    libraryQueue.drop(libraryQueueIndex + 1).take(3)
                } else {
                    prefetchedCandidates.take(3)
                },
                lastIntentLabel = lastIntentLabel,
                errorMessage = null,
                playbackMode = musicProvider.playbackMode(),
                isCurrentTrackLiked = isLiked(snapshot.currentTrack?.id),
                libraryPlayback = inLibrary,
            )
        }
    }

    private fun buildInteraction(
        track: Track,
        type: InteractionType,
        discoveryMode: DiscoveryMode,
        navigationAction: NavigationAction,
        intensity: GestureIntensity,
        velocity: Double,
    ): TrackInteraction {
        val listened = (clock.nowEpochMs() - listeningStartedAtMs).coerceAtLeast(0)
        val progress = if (track.durationMs > 0) {
            (listened.toDouble() / track.durationMs.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        return TrackInteraction(
            id = idGenerator.newInteractionId(),
            trackId = track.id,
            interactionType = type,
            gestureIntensity = intensity,
            gestureVelocity = velocity.coerceIn(0.0, 1.0),
            listeningDurationMs = listened,
            timestampEpochMs = clock.nowEpochMs(),
            artistId = track.primaryArtist.id,
            trackDurationMs = track.durationMs,
            listeningProgress = progress,
            discoveryMode = discoveryMode,
            localHour = localHour(),
            dayOfWeek = dayOfWeek(),
            navigationAction = navigationAction,
        )
    }

    private suspend fun resolveInteractionTracks(
        recent: List<TrackInteraction>,
        candidates: List<Track>,
        historyTracks: List<Track>,
        current: Track,
    ): Map<TrackId, Track> {
        val known = LinkedHashMap<TrackId, Track>()
        (historyTracks + candidates + current).forEach { known[it.id] = it }
        recent.map { it.trackId }.distinct().forEach { id ->
            if (id !in known) {
                musicProvider.getTrack(id)?.let { known[id] = it }
            }
        }
        return known.filterKeys { id -> recent.any { it.trackId == id } || id == current.id }
    }

    private fun localHour(): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = clock.nowEpochMs()
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    private fun dayOfWeek(): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = clock.nowEpochMs()
        // Calendar: Sunday=1 … Saturday=7 → ISO Monday=1 … Sunday=7
        val calendarDow = cal.get(Calendar.DAY_OF_WEEK)
        return if (calendarDow == Calendar.SUNDAY) 7 else calendarDow - 1
    }
}
