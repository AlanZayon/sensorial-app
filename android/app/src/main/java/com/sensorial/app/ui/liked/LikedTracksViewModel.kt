package com.sensorial.app.ui.liked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.preference.LikedSort
import com.sensorial.domain.preference.LikedTracks
import com.sensorial.domain.repository.InteractionRepository
import com.sensorial.domain.usecase.DiscoverySession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LikedTracksUiState(
    val allTracks: List<Track> = emptyList(),
    val visibleTracks: List<Track> = emptyList(),
    val artistFilters: List<Artist> = emptyList(),
    val genreFilters: List<GenreId> = emptyList(),
    val query: String = "",
    val selectedArtistId: ArtistId? = null,
    val selectedGenreId: GenreId? = null,
    val sort: LikedSort = LikedSort.Recent,
    val loading: Boolean = true,
    val resolving: Boolean = false,
    val totalCount: Int = 0,
    val resolvedCount: Int = 0,
    val playingTrackId: TrackId? = null,
    val errorMessage: String? = null,
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || selectedArtistId != null || selectedGenreId != null

    val selectedArtistName: String?
        get() = selectedArtistId?.let { id -> artistFilters.firstOrNull { it.id == id }?.name }

    val selectedGenreLabel: String?
        get() = selectedGenreId?.let { LikedTracks.genreLabel(it) }
}

class LikedTracksViewModel(
    private val session: DiscoverySession,
    interactionRepository: InteractionRepository,
) : ViewModel() {

    private val trackCache = LinkedHashMap<TrackId, Track>()
    private val likedAtById = LinkedHashMap<TrackId, Long>()
    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null
    private var refreshGeneration = 0
    private var resolveJob: Job? = null

    private val query = MutableStateFlow("")
    private val selectedArtistId = MutableStateFlow<ArtistId?>(null)
    private val selectedGenreId = MutableStateFlow<GenreId?>(null)
    private val sort = MutableStateFlow(LikedSort.Recent)
    private val catalog = MutableStateFlow(CatalogSnapshot())
    private val playingTrackId = MutableStateFlow<TrackId?>(null)

    private data class CatalogSnapshot(
        val tracks: List<Track> = emptyList(),
        val orderedIds: List<TrackId> = emptyList(),
        val likedAt: Map<TrackId, Long> = emptyMap(),
        val loading: Boolean = true,
        val resolving: Boolean = false,
        val errorMessage: String? = null,
    )

    private data class FilterInputs(
        val snap: CatalogSnapshot,
        val query: String,
        val artistId: ArtistId?,
        val genreId: GenreId?,
        val sort: LikedSort,
    )

    private val filterInputs = combine(
        catalog,
        query,
        selectedArtistId,
        selectedGenreId,
        sort,
    ) { snap, q, artistId, genreId, sortMode ->
        FilterInputs(snap, q, artistId, genreId, sortMode)
    }

    val state: StateFlow<LikedTracksUiState> = combine(
        filterInputs,
        playingTrackId,
    ) { inputs, playingId ->
        val snap = inputs.snap
        val visible = LikedTracks.filter(
            tracks = snap.tracks,
            query = inputs.query,
            artistId = inputs.artistId,
            genreId = inputs.genreId,
            sort = inputs.sort,
            likedAtById = snap.likedAt,
        )
        // Prefer frequent artists/genres for chips (better browse).
        val artists = snap.tracks
            .flatMap { it.artists }
            .groupingBy { it.id }
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<ArtistId, Int>> { it.value }
                    .thenBy {
                        snap.tracks.firstOrNull { t -> t.artists.any { a -> a.id == it.key } }
                            ?.artists?.firstOrNull { a -> a.id == it.key }?.name?.lowercase().orEmpty()
                    },
            )
            .mapNotNull { entry ->
                snap.tracks.asSequence()
                    .flatMap { it.artists.asSequence() }
                    .firstOrNull { it.id == entry.key }
            }
            .take(16)
        val genres = snap.tracks
            .flatMap { it.genres }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<GenreId, Int>> { it.value }
                    .thenBy { it.key.value },
            )
            .map { it.key }
            .filter { !it.value.equals("unknown", ignoreCase = true) }
            .take(14)
        LikedTracksUiState(
            allTracks = snap.tracks,
            visibleTracks = visible,
            artistFilters = artists,
            genreFilters = genres,
            query = inputs.query,
            selectedArtistId = inputs.artistId,
            selectedGenreId = inputs.genreId,
            sort = inputs.sort,
            loading = snap.loading && snap.tracks.isEmpty(),
            resolving = snap.resolving,
            totalCount = snap.orderedIds.size,
            resolvedCount = snap.tracks.size,
            playingTrackId = playingId,
            errorMessage = snap.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LikedTracksUiState(),
    )

    init {
        interactionRepository.interactions
            .onEach { refresh() }
            .launchIn(viewModelScope)
        session.state
            .onEach { feed ->
                playingTrackId.value = feed.currentTrack?.id
                val current = feed.currentTrack
                if (current != null && feed.isCurrentTrackLiked) {
                    trackCache[current.id] = current
                    if (current.id !in likedAtById) {
                        likedAtById[current.id] = System.currentTimeMillis()
                    }
                    catalog.update { snap ->
                        if (snap.orderedIds.none { it == current.id } &&
                            snap.tracks.none { it.id == current.id }
                        ) {
                            snap.copy(
                                tracks = listOf(current) + snap.tracks,
                                orderedIds = listOf(current.id) + snap.orderedIds,
                                likedAt = likedAtById.toMap(),
                            )
                        } else {
                            snap.copy(
                                tracks = snap.tracks.map { if (it.id == current.id) current else it },
                                likedAt = likedAtById.toMap(),
                            )
                        }
                    }
                } else if (current != null && !feed.isCurrentTrackLiked) {
                    likedAtById.remove(current.id)
                    catalog.update { snap ->
                        snap.copy(
                            tracks = snap.tracks.filterNot { it.id == current.id },
                            orderedIds = snap.orderedIds.filterNot { it == current.id },
                            likedAt = likedAtById.toMap(),
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleArtistFilter(artistId: ArtistId) {
        selectedArtistId.value = if (selectedArtistId.value == artistId) null else artistId
    }

    fun toggleGenreFilter(genreId: GenreId) {
        selectedGenreId.value = if (selectedGenreId.value == genreId) null else genreId
    }

    fun setSort(value: LikedSort) {
        sort.value = value
    }

    fun clearFilters() {
        query.value = ""
        selectedArtistId.value = null
        selectedGenreId.value = null
    }

    fun refresh() {
        refreshJob?.cancel()
        resolveJob?.cancel()
        val generation = ++refreshGeneration
        refreshJob = viewModelScope.launch {
            refreshMutex.withLock {
                if (generation != refreshGeneration) return@withLock
                catalog.update { it.copy(loading = true, errorMessage = null) }
                runCatching {
                    val refs = session.likedTrackRefs()
                    likedAtById.clear()
                    refs.forEach { likedAtById[it.trackId] = it.likedAtEpochMs }
                    val ids = refs.map { it.trackId }
                    val local = session.likedTracksLocalCache()
                    local.forEach { (id, track) -> trackCache[id] = track }
                    val known = ids.mapNotNull { trackCache[it] }
                    CatalogBootstrap(ids = ids, known = known, likedAt = likedAtById.toMap())
                }.onSuccess { boot ->
                    if (generation != refreshGeneration) return@onSuccess
                    catalog.value = CatalogSnapshot(
                        tracks = boot.known,
                        orderedIds = boot.ids,
                        likedAt = boot.likedAt,
                        loading = false,
                        resolving = boot.ids.size > boot.known.size,
                    )
                    resolveMissing(generation, boot.ids)
                }.onFailure { error ->
                    if (generation != refreshGeneration) return@onFailure
                    catalog.update {
                        it.copy(
                            loading = false,
                            resolving = false,
                            errorMessage = error.message ?: "Não foi possível carregar curtidas",
                        )
                    }
                }
            }
        }
    }

    fun play(track: Track) {
        val queue = state.value.visibleTracks
        viewModelScope.launch {
            runCatching { session.playFromLibrary(track, queue) }
                .onFailure { error ->
                    catalog.update {
                        it.copy(errorMessage = error.message ?: "Falha ao tocar")
                    }
                }
        }
    }

    private fun resolveMissing(generation: Int, orderedIds: List<TrackId>) {
        val missing = orderedIds.filter { it !in trackCache }
        if (missing.isEmpty()) {
            catalog.update { it.copy(resolving = false) }
            return
        }
        resolveJob = viewModelScope.launch {
            catalog.update { it.copy(resolving = true) }
            missing.chunked(50).forEach { chunk ->
                if (generation != refreshGeneration) return@launch
                val resolved = runCatching { session.resolveLikedTracks(chunk) }.getOrDefault(emptyMap())
                resolved.forEach { (id, track) -> trackCache[id] = track }
                if (generation != refreshGeneration) return@launch
                val orderedTracks = orderedIds.mapNotNull { trackCache[it] }
                catalog.update {
                    it.copy(
                        tracks = orderedTracks,
                        likedAt = likedAtById.toMap(),
                        resolving = orderedTracks.size < orderedIds.size,
                    )
                }
            }
            if (generation == refreshGeneration) {
                catalog.update { it.copy(resolving = false) }
            }
        }
    }

    private data class CatalogBootstrap(
        val ids: List<TrackId>,
        val known: List<Track>,
        val likedAt: Map<TrackId, Long>,
    )
}

class LikedTracksViewModelFactory(
    private val session: DiscoverySession,
    private val interactionRepository: InteractionRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LikedTracksViewModel::class.java))
        return LikedTracksViewModel(session, interactionRepository) as T
    }
}
