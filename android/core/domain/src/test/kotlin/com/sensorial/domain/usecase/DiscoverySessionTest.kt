package com.sensorial.domain.usecase

import com.sensorial.domain.feed.PlaybackStatus
import com.sensorial.domain.history.PlaybackHistory
import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.InteractionId
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction
import com.sensorial.domain.preference.UserPreferenceProfile
import com.sensorial.domain.provider.MusicProvider
import com.sensorial.domain.recommendation.RecommendationContext
import com.sensorial.domain.recommendation.ScoredHeuristicRecommendationEngine
import com.sensorial.domain.repository.AppSettings
import com.sensorial.domain.repository.AppSettingsRepository
import com.sensorial.domain.repository.InteractionRepository
import com.sensorial.domain.repository.PlaybackHistoryRepository
import com.sensorial.domain.repository.PlaybackHistorySnapshot
import com.sensorial.domain.repository.UserPreferenceRepository
import com.sensorial.domain.repository.toSnapshot
import com.sensorial.domain.util.Clock
import com.sensorial.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DiscoverySessionTest {

    private fun track(
        id: String,
        genre: String,
        features: AudioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
        durationMs: Long = 180_000L,
    ) = Track(
        id = TrackId(id),
        title = id,
        artists = listOf(Artist(ArtistId("art-$id"), "Artist $id")),
        album = Album("al-$id", "Album"),
        genres = listOf(GenreId(genre)),
        audioFeatures = features,
        durationMs = durationMs,
    )

    @Test
    fun `start seeds history and playing state`() = runTest {
        val catalog = listOf(track("A", "pop"), track("B", "rock"), track("C", "jazz"))
        val session = sessionFor(catalog)
        session.start()
        assertEquals(PlaybackStatus.Playing, session.state.value.status)
        assertEquals("A", session.state.value.currentTrack?.id?.value)
    }

    @Test
    fun `advance at tip recommends new track`() = runTest {
        val catalog = listOf(track("A", "pop"), track("B", "rock"), track("C", "jazz"))
        val session = sessionFor(catalog)
        session.start()
        session.advance()
        assertNotEquals("A", session.state.value.currentTrack?.id?.value)
        assertTrue(session.state.value.canGoBack)
    }

    @Test
    fun `goBack restores previous without new recommendation`() = runTest {
        val catalog = listOf(track("A", "pop"), track("B", "pop"), track("C", "rock"), track("D", "jazz"))
        val interactions = FakeInteractionRepository()
        val session = sessionFor(catalog, interactionRepository = interactions)
        session.start()
        session.discover(DiscoveryMode.Deepen)
        val after = session.state.value.currentTrack!!.id.value
        session.goBack()
        assertEquals("A", session.state.value.currentTrack?.id?.value)
        assertTrue(session.state.value.canGoForward)
        assertTrue(interactions.items.any { it.interactionType == InteractionType.NavigationReturn })
        assertTrue(interactions.items.none { it.interactionType == InteractionType.ExplicitLike })
        session.advance()
        assertEquals(after, session.state.value.currentTrack?.id?.value)
    }

    @Test
    fun `deepen and explore stay on single timeline`() = runTest {
        val catalog = listOf(
            track("A", "pop"),
            track("B", "pop"),
            track("C", "rock"),
            track("D", "jazz"),
            track("E", "ambient"),
        )
        val session = sessionFor(catalog)
        session.start()
        session.discover(DiscoveryMode.Deepen)
        val mid = session.state.value.currentTrack!!.id.value
        session.discover(DiscoveryMode.Explore)
        session.goBack()
        assertEquals(mid, session.state.value.currentTrack?.id?.value)
        session.goBack()
        assertEquals("A", session.state.value.currentTrack?.id?.value)
    }

    @Test
    fun `explicit like does not change current track`() = runTest {
        val catalog = listOf(track("A", "pop"), track("B", "rock"))
        val preferences = FakePreferenceRepository()
        val session = sessionFor(catalog, preferenceRepository = preferences)
        session.start()
        session.explicitLike()
        assertEquals("A", session.state.value.currentTrack?.id?.value)
        assertTrue(session.state.value.isCurrentTrackLiked)
        assertTrue(preferences.get().genreScore(GenreId("pop")) > 0.0)
        assertEquals(1, preferences.get().positiveSignalCount)
    }

    @Test
    fun `toggling like again removes preference impact`() = runTest {
        val catalog = listOf(track("A", "pop"), track("B", "rock"))
        val preferences = FakePreferenceRepository()
        val interactions = FakeInteractionRepository()
        val session = sessionFor(
            catalog,
            preferenceRepository = preferences,
            interactionRepository = interactions,
        )
        session.start()
        session.explicitLike()
        val likedScore = preferences.get().genreScore(GenreId("pop"))
        assertTrue(likedScore > 0.0)
        session.explicitLike()
        assertTrue(!session.state.value.isCurrentTrackLiked)
        assertEquals(0, preferences.get().positiveSignalCount)
        assertTrue(preferences.get().genreScore(GenreId("pop")) < likedScore)
        assertTrue(interactions.items.any { it.interactionType == InteractionType.ExplicitUnlike })
    }

    @Test
    fun `liked state persists when returning to track`() = runTest {
        val catalog = listOf(track("A", "pop"), track("B", "rock"), track("C", "jazz"))
        val session = sessionFor(catalog)
        session.start()
        session.explicitLike()
        assertTrue(session.state.value.isCurrentTrackLiked)
        session.advance()
        assertEquals("B", session.state.value.currentTrack?.id?.value)
        assertTrue(!session.state.value.isCurrentTrackLiked)
        session.goBack()
        assertEquals("A", session.state.value.currentTrack?.id?.value)
        assertTrue(session.state.value.isCurrentTrackLiked)
    }

    @Test
    fun `second like tap undoes like and preference impact`() = runTest {
        val catalog = listOf(track("A", "pop"), track("B", "rock"))
        val preferences = FakePreferenceRepository()
        val session = sessionFor(catalog, preferenceRepository = preferences)
        session.start()
        session.explicitLike()
        assertTrue(session.state.value.isCurrentTrackLiked)
        assertTrue(preferences.get().genreScore(GenreId("pop")) > 0.0)
        session.explicitLike()
        assertTrue(!session.state.value.isCurrentTrackLiked)
        assertEquals(0, preferences.get().positiveSignalCount)
    }

    @Test
    fun `long listen navigation raises listening score without like`() = runTest {
        val clock = MutableClock(1_000L)
        val catalog = listOf(track("A", "indie", durationMs = 100_000L), track("B", "pop"), track("C", "jazz"))
        val preferences = FakePreferenceRepository()
        val session = sessionFor(catalog, clock = clock, preferenceRepository = preferences)
        session.start()
        clock.advance(90_000)
        session.advance()
        assertEquals(0, preferences.get().positiveSignalCount)
        assertTrue(preferences.get().genreListeningScore(GenreId("indie")) > 0.0)
    }

    @Test
    fun `navigation does not create explicit like or dislike`() = runTest {
        val catalog = listOf(track("A", "pop"), track("B", "rock"), track("C", "jazz"))
        val interactions = FakeInteractionRepository()
        val session = sessionFor(catalog, interactionRepository = interactions)
        session.start()
        session.advance()
        assertTrue(interactions.items.any { it.interactionType == InteractionType.NavigationAdvance })
        assertTrue(interactions.items.none { it.interactionType == InteractionType.ExplicitLike })
        assertTrue(interactions.items.none { it.interactionType == InteractionType.ExplicitDislike })
    }

    private fun sessionFor(
        catalog: List<Track>,
        clock: MutableClock = MutableClock(1_000L),
        historyRepository: FakeHistoryRepository = FakeHistoryRepository(),
        interactionRepository: FakeInteractionRepository = FakeInteractionRepository(),
        preferenceRepository: FakePreferenceRepository = FakePreferenceRepository(),
        getCandidates: suspend (RecommendationContext) -> List<Track> = { catalog },
        playTrack: suspend (TrackId) -> Unit = {},
    ): DiscoverySession {
        var interactionCounter = 0
        val provider = object : MusicProvider {
            override suspend fun searchTracks(query: String) = catalog.filter { it.title.contains(query, true) }
            override suspend fun getTrack(id: TrackId) = catalog.firstOrNull { it.id == id }
            override suspend fun getCandidates(context: RecommendationContext) = getCandidates(context)
            override suspend fun playTrack(id: TrackId) = playTrack(id)
        }
        return DiscoverySession(
            musicProvider = provider,
            historyRepository = historyRepository,
            interactionRepository = interactionRepository,
            preferenceRepository = preferenceRepository,
            settingsRepository = FakeSettingsRepository(),
            recommendationEngine = ScoredHeuristicRecommendationEngine(),
            clock = clock,
            idGenerator = IdGenerator { InteractionId("i-${interactionCounter++}") },
        )
    }
}

private class MutableClock(private var now: Long) : Clock {
    override fun nowEpochMs(): Long = now
    fun advance(deltaMs: Long) {
        now += deltaMs
    }
}

private class FakeHistoryRepository : PlaybackHistoryRepository {
    private var delegate = PlaybackHistory()
    private val _state = MutableStateFlow(delegate.toSnapshot())
    override val history: StateFlow<PlaybackHistorySnapshot> = _state.asStateFlow()

    private fun emit() {
        _state.value = delegate.toSnapshot()
    }

    override suspend fun push(
        track: Track,
        discoveryMode: DiscoveryMode,
        recommendedAtEpochMs: Long,
    ) {
        delegate.push(track, discoveryMode, recommendedAtEpochMs)
        emit()
    }

    override suspend fun previous(): Track = delegate.previous().also { emit() }
    override suspend fun next(): Track = delegate.next().also { emit() }
    override suspend fun current(): Track? = delegate.currentTrack
    override suspend fun canGoBack(): Boolean = delegate.canGoBack
    override suspend fun canGoForward(): Boolean = delegate.canGoForward
    override suspend fun updateCurrentListeningProgress(progress: Double) {
        delegate.updateCurrentListeningProgress(progress)
        emit()
    }

    override suspend fun reset(seed: Track?) {
        delegate = PlaybackHistory()
        if (seed != null) delegate.push(seed)
        emit()
    }
}

private class FakeInteractionRepository : InteractionRepository {
    val items = mutableListOf<TrackInteraction>()
    override val interactions: Flow<List<TrackInteraction>> = flowOf(emptyList())
    override suspend fun append(interaction: TrackInteraction) {
        items += interaction
    }
    override suspend fun recent(limit: Int): List<TrackInteraction> = items.takeLast(limit)
    override suspend fun clear() {
        items.clear()
    }
}

private class FakePreferenceRepository : UserPreferenceRepository {
    private val _profile = MutableStateFlow(UserPreferenceProfile())
    override val profile: StateFlow<UserPreferenceProfile> = _profile.asStateFlow()
    override suspend fun get(): UserPreferenceProfile = _profile.value
    override suspend fun save(profile: UserPreferenceProfile) {
        _profile.value = profile
    }
    override suspend fun clear() {
        _profile.value = UserPreferenceProfile()
    }
}

private class FakeSettingsRepository : AppSettingsRepository {
    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    override suspend fun get(): AppSettings = _settings.value
    override suspend fun save(settings: AppSettings) {
        _settings.value = settings
    }
}
