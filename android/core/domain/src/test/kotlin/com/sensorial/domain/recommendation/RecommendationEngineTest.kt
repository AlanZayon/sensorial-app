package com.sensorial.domain.recommendation

import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.GestureIntensity
import com.sensorial.domain.model.HorizontalIntent
import com.sensorial.domain.model.InteractionId
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction
import com.sensorial.domain.model.VerticalIntent
import com.sensorial.domain.preference.UserPreferenceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecommendationEngineTest {

    private val engine = ScoredHeuristicRecommendationEngine()

    private fun track(
        id: String,
        genres: List<String>,
        artist: String = "artist-$id",
        features: AudioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
    ) = Track(
        id = TrackId(id),
        title = id,
        artists = listOf(Artist(ArtistId(artist), artist)),
        album = Album("al-$id", "Album $id"),
        genres = genres.map(::GenreId),
        audioFeatures = features,
    )

    private fun interaction(
        trackId: String,
        vertical: VerticalIntent = VerticalIntent.None,
        horizontal: HorizontalIntent = HorizontalIntent.None,
        intensity: GestureIntensity = GestureIntensity.High,
        velocity: Double = 1.0,
        listeningMs: Long = 25_000,
        id: String = "i-$trackId",
    ) = TrackInteraction(
        id = InteractionId(id),
        trackId = TrackId(trackId),
        interactionType = InteractionType.Preference,
        verticalIntent = vertical,
        horizontalIntent = horizontal,
        gestureIntensity = intensity,
        gestureVelocity = velocity,
        listeningDurationMs = listeningMs,
        timestampEpochMs = 1L,
    )

    @Test
    fun `positive genre preference ranks matching track higher`() {
        val profile = UserPreferenceProfile(
            genreScores = mapOf(GenreId("pop") to 0.9, GenreId("metal") to -0.8),
            positiveSignalCount = 1,
        )
        val pop = track("pop1", listOf("pop"))
        val metal = track("metal1", listOf("metal"))
        val ranked = engine.rank(profile, RecommendationContext(null, emptyList(), emptySet()), listOf(metal, pop))
        assertEquals("pop1", ranked.first().track.id.value)
    }

    @Test
    fun `negative preference penalizes disliked genres`() {
        val profile = UserPreferenceProfile(
            genreScores = mapOf(GenreId("metal") to -0.9),
            negativeSignalCount = 1,
        )
        val metal = track("metal1", listOf("metal"))
        val jazz = track("jazz1", listOf("jazz"))
        val ranked = engine.rank(profile, RecommendationContext(null, emptyList(), emptySet()), listOf(metal, jazz))
        assertEquals("jazz1", ranked.first().track.id.value)
    }

    @Test
    fun `exploration prefers novel genres when bias is high`() {
        val current = track("cur", listOf("pop"), artist = "a")
        val similar = track("sim", listOf("pop"), artist = "a")
        val novel = track("nov", listOf("ambient"), artist = "b")
        val profile = UserPreferenceProfile(explorationPreference = 1.0, positiveSignalCount = 1)
        val ranked = engine.rank(
            profile,
            RecommendationContext(
                currentTrack = current,
                recentInteractions = emptyList(),
                excludedIds = emptySet(),
                discoveryMode = DiscoveryMode.Explore,
            ),
            listOf(similar, novel),
        )
        assertEquals("nov", ranked.first().track.id.value)
    }

    @Test
    fun `excluded ids are not recommended`() {
        val profile = UserPreferenceProfile(positiveSignalCount = 1)
        val a = track("a", listOf("pop"))
        val b = track("b", listOf("pop"))
        val result = engine.recommend(
            profile,
            RecommendationContext(null, emptyList(), setOf(TrackId("a"))),
            listOf(a, b),
        )
        assertEquals("b", result?.id?.value)
    }

    @Test
    fun `weights affect ordering`() {
        val profile = UserPreferenceProfile(
            genreScores = mapOf(GenreId("pop") to 1.0),
            artistScores = mapOf(ArtistId("star") to 1.0),
            positiveSignalCount = 1,
        )
        val genreMatch = track("g", listOf("pop"), artist = "other")
        val artistMatch = track("a", listOf("rock"), artist = "star")
        val genreHeavy = ScoredHeuristicRecommendationEngine(
            RecommendationWeights(
                genre = 5.0,
                artist = 0.1,
                features = 0.0,
                recentSignals = 0.0,
                exploration = 0.0,
                listening = 0.0,
                coldStart = 0.0,
            ),
        )
        val ranked = genreHeavy.rank(
            profile,
            RecommendationContext(null, emptyList(), emptySet()),
            listOf(artistMatch, genreMatch),
        )
        assertEquals("g", ranked.first().track.id.value)
    }

    @Test
    fun `feature similarity contributes when preferred features exist`() {
        val preferred = AudioFeatures(0.9, 0.8, 0.7, 128.0)
        val profile = UserPreferenceProfile(preferredFeatures = preferred, positiveSignalCount = 1)
        val close = track("close", listOf("edm"), features = AudioFeatures(0.88, 0.79, 0.68, 126.0))
        val far = track("far", listOf("edm"), features = AudioFeatures(0.1, 0.1, 0.1, 70.0))
        val ranked = engine.rank(profile, RecommendationContext(null, emptyList(), emptySet()), listOf(far, close))
        assertEquals("close", ranked.first().track.id.value)
    }

    @Test
    fun `strong positive recent signal boosts same genre candidate`() {
        val liked = track("liked", listOf("pop"), artist = "luna")
        val popNext = track("pop2", listOf("pop"), artist = "other-pop")
        val metal = track("metal1", listOf("metal"), artist = "iron")
        val interaction = interaction("liked", vertical = VerticalIntent.Positive)
        val profile = UserPreferenceProfile(positiveSignalCount = 1)
        val ranked = engine.rank(
            profile,
            RecommendationContext(
                currentTrack = liked,
                recentInteractions = listOf(interaction),
                excludedIds = setOf(liked.id),
                interactionTracks = mapOf(liked.id to liked),
            ),
            listOf(metal, popNext),
        )
        assertEquals("pop2", ranked.first().track.id.value)
    }

    @Test
    fun `similar intent ranks proximate track over novel`() {
        val current = track("cur", listOf("electronic"), artist = "circuit", features = AudioFeatures(0.85, 0.9, 0.5, 128.0))
        val proximate = track(
            "near",
            listOf("electronic"),
            artist = "circuit-b",
            features = AudioFeatures(0.82, 0.88, 0.48, 126.0),
        )
        val novel = track("far", listOf("folk"), artist = "cedar", features = AudioFeatures(0.2, 0.25, 0.6, 90.0))
        val interaction = interaction("cur", horizontal = HorizontalIntent.Similar)
        val profile = UserPreferenceProfile(explorationPreference = 0.2, positiveSignalCount = 1)
        val ranked = engine.rank(
            profile,
            RecommendationContext(
                currentTrack = current,
                recentInteractions = listOf(interaction),
                excludedIds = setOf(current.id),
                interactionTracks = mapOf(current.id to current),
            ),
            listOf(novel, proximate),
        )
        assertEquals("near", ranked.first().track.id.value)
    }

    @Test
    fun `explore intent ranks novel track over proximate`() {
        val current = track("cur", listOf("pop"), artist = "luna")
        val proximate = track("near", listOf("pop"), artist = "luna")
        val novel = track("far", listOf("ambient"), artist = "aeon")
        val interaction = interaction("cur", horizontal = HorizontalIntent.Explore)
        val profile = UserPreferenceProfile(explorationPreference = 0.85, positiveSignalCount = 1)
        val ranked = engine.rank(
            profile,
            RecommendationContext(
                currentTrack = current,
                recentInteractions = listOf(interaction),
                excludedIds = setOf(current.id),
                interactionTracks = mapOf(current.id to current),
            ),
            listOf(proximate, novel),
        )
        assertEquals("far", ranked.first().track.id.value)
    }

    @Test
    fun `breakdown total matches score`() {
        val profile = UserPreferenceProfile(
            genreScores = mapOf(GenreId("jazz") to 0.4),
            positiveSignalCount = 1,
        )
        val candidate = track("j1", listOf("jazz"))
        val scored = engine.scoreDetailed(
            candidate,
            profile,
            RecommendationContext(null, emptyList(), emptySet()),
        )
        assertEquals(scored.score, scored.breakdown.total)
    }

    @Test
    fun `cold start produces stable deterministic ranking`() {
        val profile = UserPreferenceProfile()
        val a = track("alpha", listOf("x"), features = AudioFeatures(0.5, 0.5, 0.5, 120.0))
        val b = track("beta", listOf("y"), features = AudioFeatures(0.5, 0.5, 0.5, 120.0))
        val first = engine.rank(profile, RecommendationContext(null, emptyList(), emptySet()), listOf(a, b))
        val second = engine.rank(profile, RecommendationContext(null, emptyList(), emptySet()), listOf(b, a))
        assertEquals(first.map { it.track.id.value }, second.map { it.track.id.value })
    }

    @Test
    fun `deepen mode ranks similar track above explore mode preference`() {
        val current = track("cur", listOf("indie"), artist = "band", features = AudioFeatures(0.6, 0.5, 0.5, 110.0))
        val similar = track("sim", listOf("indie"), artist = "band-b", features = AudioFeatures(0.58, 0.52, 0.48, 112.0))
        val novel = track("nov", listOf("trance"), artist = "dj", features = AudioFeatures(0.9, 0.9, 0.3, 140.0))
        val profile = UserPreferenceProfile(explorationPreference = 0.5, positiveSignalCount = 1)
        val deepen = engine.rank(
            profile,
            RecommendationContext(
                currentTrack = current,
                recentInteractions = emptyList(),
                excludedIds = setOf(current.id),
                discoveryMode = DiscoveryMode.Deepen,
                recentTracks = listOf(current),
            ),
            listOf(novel, similar),
        )
        val explore = engine.rank(
            profile,
            RecommendationContext(
                currentTrack = current,
                recentInteractions = emptyList(),
                excludedIds = setOf(current.id),
                discoveryMode = DiscoveryMode.Explore,
                recentTracks = listOf(current),
            ),
            listOf(novel, similar),
        )
        assertEquals("sim", deepen.first().track.id.value)
        assertEquals("nov", explore.first().track.id.value)
    }

    @Test
    fun `deepen after same-artist streak prefers related artist over more of same`() {
        val current = track("cur", listOf("indie"), artist = "band-a", features = AudioFeatures(0.6, 0.5, 0.5, 110.0))
        val prior1 = track("p1", listOf("indie"), artist = "band-a", features = AudioFeatures(0.61, 0.5, 0.5, 111.0))
        val prior2 = track("p2", listOf("indie"), artist = "band-a", features = AudioFeatures(0.59, 0.51, 0.49, 109.0))
        val sameArtist = track("same", listOf("indie"), artist = "band-a", features = AudioFeatures(0.6, 0.5, 0.5, 110.0))
        val related = track("rel", listOf("indie"), artist = "band-b", features = AudioFeatures(0.58, 0.52, 0.48, 112.0))
        val ranked = engine.rank(
            UserPreferenceProfile(positiveSignalCount = 1),
            RecommendationContext(
                currentTrack = current,
                recentInteractions = emptyList(),
                excludedIds = setOf(current.id, prior1.id, prior2.id),
                relatedArtistIds = setOf(ArtistId("band-b")),
                discoveryMode = DiscoveryMode.Deepen,
                recentTracks = listOf(prior1, prior2, current),
            ),
            listOf(sameArtist, related),
        )
        assertEquals("rel", ranked.first().track.id.value)
    }

    @Test
    fun `remaster of current track is filtered out of ranking`() {
        val current = Track(
            id = TrackId("cur"),
            title = "Neon Pulse",
            artists = listOf(Artist(ArtistId("artist-a"), "artist-a")),
            album = Album("al", "Album"),
            genres = listOf(GenreId("indie")),
            audioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
        )
        val remaster = Track(
            id = TrackId("rem"),
            title = "Neon Pulse - Remastered 2020",
            artists = listOf(Artist(ArtistId("artist-a"), "artist-a")),
            album = Album("al2", "Album 2"),
            genres = listOf(GenreId("indie")),
            audioFeatures = AudioFeatures(0.51, 0.49, 0.5, 121.0),
        )
        val other = track("other", listOf("indie"), artist = "artist-b")
        val ranked = engine.rank(
            UserPreferenceProfile(positiveSignalCount = 1),
            RecommendationContext(
                currentTrack = current,
                recentInteractions = emptyList(),
                excludedIds = setOf(current.id),
                recentTracks = listOf(current),
            ),
            listOf(remaster, other),
        )
        assertEquals(listOf("other"), ranked.map { it.track.id.value })
    }

    @Test
    fun `listening behavior score ranks without explicit likes`() {
        val profile = UserPreferenceProfile(
            genreListeningScores = mapOf(GenreId("soul") to 0.9),
        )
        val soul = track("soul1", listOf("soul"))
        val punk = track("punk1", listOf("punk"))
        val ranked = engine.rank(
            profile,
            RecommendationContext(null, emptyList(), emptySet()),
            listOf(punk, soul),
        )
        assertEquals("soul1", ranked.first().track.id.value)
    }

    @Test
    fun `quick skipped artist stays downranked after explorationBoost resets`() {
        val skipped = track("skip", listOf("indie"), artist = "band-a", features = AudioFeatures(0.6, 0.5, 0.5, 110.0))
        val similar = track("sim", listOf("indie"), artist = "band-a", features = AudioFeatures(0.58, 0.52, 0.48, 112.0))
        val other = track("oth", listOf("soul"), artist = "band-b", features = AudioFeatures(0.4, 0.4, 0.6, 95.0))
        val skipInteraction = TrackInteraction(
            id = InteractionId("i-skip"),
            trackId = skipped.id,
            interactionType = InteractionType.NavigationAdvance,
            listeningDurationMs = 2_000,
            timestampEpochMs = 1,
            listeningProgress = 0.05,
            artistId = skipped.primaryArtist.id,
        )
        val profile = UserPreferenceProfile().applying(skipped, skipInteraction)
        val ranked = engine.rank(
            profile,
            RecommendationContext(
                currentTrack = other,
                recentInteractions = listOf(skipInteraction),
                excludedIds = setOf(skipped.id, other.id),
                interactionTracks = mapOf(skipped.id to skipped, other.id to other),
                recentTracks = listOf(skipped, other),
                sessionAffinity = SessionAffinity.EMPTY, // explorationBoost already reset
            ),
            listOf(similar, other.copy(id = TrackId("fresh"), title = "fresh")),
        )
        assertEquals("fresh", ranked.first().track.id.value)
    }

    @Test
    fun `higher intensity recent signal outranks weaker same-genre signal competing genres`() {
        val liked = track("liked", listOf("latin"))
        val latin = track("latin2", listOf("latin"))
        val rock = track("rock1", listOf("rock"))
        val strong = interaction(
            "liked",
            vertical = VerticalIntent.Positive,
            intensity = GestureIntensity.High,
            velocity = 1.0,
            listeningMs = 30_000,
        )
        val weakEngine = ScoredHeuristicRecommendationEngine(
            weights = RecommendationWeights(recentSignals = 2.0, genre = 0.1, exploration = 0.0, listening = 0.0, coldStart = 0.0),
        )
        val ranked = weakEngine.rank(
            UserPreferenceProfile(positiveSignalCount = 1),
            RecommendationContext(
                currentTrack = liked,
                recentInteractions = listOf(strong),
                excludedIds = setOf(liked.id),
                interactionTracks = mapOf(liked.id to liked),
            ),
            listOf(rock, latin),
        )
        assertEquals("latin2", ranked.first().track.id.value)
        assertTrue(ranked.first().breakdown.recent > ranked.last().breakdown.recent)
    }
}
