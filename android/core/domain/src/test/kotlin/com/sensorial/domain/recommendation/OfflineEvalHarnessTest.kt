package com.sensorial.domain.recommendation

import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.preference.UserPreferenceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Offline evaluation fixtures — guards regression of mode behavior, diversity, remasters,
 * cold/warm behavior, and listening-proxy metrics.
 */
class OfflineEvalHarnessTest {
    private val engine = ScoredHeuristicRecommendationEngine(useModePresets = true, useMmr = true)

    private fun track(
        id: String,
        genres: List<String>,
        artist: String,
        popularity: Int = 50,
        year: Int? = 2020,
        features: AudioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
        durationMs: Long = 180_000L,
    ) = Track(
        id = TrackId(id),
        title = id,
        artists = listOf(Artist(ArtistId(artist), artist)),
        album = Album("al-$id", "Album", releaseYear = year),
        genres = genres.map(::GenreId),
        audioFeatures = features,
        popularity = popularity,
        durationMs = durationMs,
    )

    /** Frozen A/B fixture pool shared across metric assertions. */
    private fun fixturePool(current: Track): List<Track> = listOf(
        track(
            "sim",
            listOf("indie"),
            "band-b",
            popularity = 70,
            features = AudioFeatures(0.58, 0.52, 0.48, 112.0),
        ),
        track(
            "nov",
            listOf("trance"),
            "dj",
            popularity = 35,
            features = AudioFeatures(0.9, 0.9, 0.3, 140.0),
        ),
        track("soul", listOf("soul"), "s", popularity = 55),
        track("rock", listOf("rock"), "r", popularity = 48),
        track("metal", listOf("metal"), "m", popularity = 42),
        track("t6", listOf("indie"), "a6", popularity = 44),
        track("t7", listOf("pop"), "a7", popularity = 46),
        track("t8", listOf("electronic"), "a8", popularity = 40),
    ) + listOf(current)

    @Test
    fun `liker profile prefers liked genre at precision1`() {
        val profile = UserPreferenceProfile(
            genreScores = mapOf(GenreId("soul") to 0.9),
            positiveSignalCount = 3,
        )
        val pool = listOf(
            track("rock", listOf("rock"), "r"),
            track("soul", listOf("soul"), "s"),
            track("metal", listOf("metal"), "m"),
        )
        val pick = engine.recommend(
            profile,
            RecommendationContext(null, emptyList(), emptySet()),
            pool,
        )
        assertEquals("soul", pick?.id?.value)
    }

    @Test
    fun `deepen and explore diverge on same pool`() {
        val current = track(
            "cur",
            listOf("indie"),
            "band",
            features = AudioFeatures(0.6, 0.5, 0.5, 110.0),
        )
        val similar = track(
            "sim",
            listOf("indie"),
            "band-b",
            popularity = 70,
            features = AudioFeatures(0.58, 0.52, 0.48, 112.0),
        )
        val novel = track(
            "nov",
            listOf("trance"),
            "dj",
            popularity = 35,
            features = AudioFeatures(0.9, 0.9, 0.3, 140.0),
        )
        val profile = UserPreferenceProfile(positiveSignalCount = 1)
        val deepen = engine.rank(
            profile,
            RecommendationContext(
                currentTrack = current,
                recentInteractions = emptyList(),
                excludedIds = setOf(current.id),
                discoveryMode = DiscoveryMode.Deepen,
                recentTracks = listOf(current),
                relatedArtistIds = setOf(ArtistId("band-b")),
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
        assertTrue(deepen.first().track.id != explore.first().track.id)
    }

    @Test
    fun `artist diversity at top5 for cold exploration pool`() {
        val current = track("cur", listOf("pop"), "a0")
        val pool = (1..8).map { i ->
            track("t$i", listOf("pop"), if (i <= 2) "a0" else "a$i", popularity = 40 + i)
        }
        val ranked = engine.rank(
            UserPreferenceProfile(),
            RecommendationContext(
                currentTrack = current,
                recentInteractions = emptyList(),
                excludedIds = setOf(current.id),
                discoveryMode = DiscoveryMode.Explore,
                recentTracks = listOf(current) + pool.take(2),
            ),
            pool,
        ).take(5)
        val distinctArtists = OfflineEvalMetrics.artistDiversityAtK(ranked, 5)
        assertTrue(distinctArtists >= 3, "expected diverse top5, got $distinctArtists")
    }

    @Test
    fun `remaster of recent work never ranks`() {
        val heard = Track(
            id = TrackId("orig"),
            title = "Neon Pulse",
            artists = listOf(Artist(ArtistId("x"), "x")),
            album = Album("a", "A"),
            genres = listOf(GenreId("indie")),
            audioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
        )
        val remaster = Track(
            id = TrackId("rem"),
            title = "Neon Pulse - Remastered",
            artists = listOf(Artist(ArtistId("x"), "x")),
            album = Album("b", "B"),
            genres = listOf(GenreId("indie")),
            audioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
        )
        val other = track("other", listOf("indie"), "y")
        val ranked = engine.rank(
            UserPreferenceProfile(positiveSignalCount = 1),
            RecommendationContext(
                currentTrack = heard,
                recentInteractions = emptyList(),
                excludedIds = setOf(heard.id),
                recentTracks = listOf(heard),
            ),
            listOf(remaster, other),
        )
        assertEquals(listOf("other"), ranked.map { it.track.id.value })
    }

    @Test
    fun `local similarity ranks proximate features higher`() {
        val sim = LocalFeatureTrackSimilarity()
        val a = track("a", listOf("jazz"), "j", features = AudioFeatures(0.4, 0.3, 0.5, 90.0))
        val near = track("near", listOf("jazz"), "j2", features = AudioFeatures(0.42, 0.32, 0.48, 92.0))
        val far = track("far", listOf("metal"), "m", features = AudioFeatures(0.95, 0.8, 0.2, 160.0))
        assertTrue(sim.similarity(a, near) > sim.similarity(a, far))
    }

    @Test
    fun `offline report exposes mode divergence diversity remaster and proxies`() {
        val current = track(
            "cur",
            listOf("indie"),
            "band",
            features = AudioFeatures(0.6, 0.5, 0.5, 110.0),
        )
        val pool = fixturePool(current).filter { it.id != current.id }
        val cold = UserPreferenceProfile()
        val warm = UserPreferenceProfile(
            genreScores = mapOf(GenreId("indie") to 0.8),
            preferredFeatures = AudioFeatures(0.58, 0.5, 0.5, 112.0),
            positiveSignalCount = 4,
        )
        val deepenCtx = RecommendationContext(
            currentTrack = current,
            recentInteractions = emptyList(),
            excludedIds = setOf(current.id),
            discoveryMode = DiscoveryMode.Deepen,
            recentTracks = listOf(current),
            relatedArtistIds = setOf(ArtistId("band-b")),
        )
        val exploreCtx = deepenCtx.copy(
            discoveryMode = DiscoveryMode.Explore,
            relatedArtistIds = emptySet(),
        )
        val deepen = engine.rank(warm, deepenCtx, pool)
        val explore = engine.rank(warm, exploreCtx, pool)
        val remaster = Track(
            id = TrackId("rem"),
            title = "cur - Remastered",
            artists = listOf(Artist(ArtistId("band"), "band")),
            album = Album("b", "B"),
            genres = listOf(GenreId("indie")),
            audioFeatures = AudioFeatures(0.6, 0.5, 0.5, 110.0),
        )
        val remasterRanked = engine.rank(
            warm,
            deepenCtx.copy(recentTracks = listOf(current)),
            pool + remaster,
        )
        val coldPick = engine.recommend(cold, deepenCtx, pool)
        val warmPick = engine.recommend(warm, deepenCtx, pool)
        val streakRate = OfflineEvalMetrics.deepenSameArtistStreakRate(
            picks = deepen.take(3).map { it.track },
            seedArtistId = "band",
        )
        val report = OfflineEvalReport(
            modeDivergence = deepen.first().track.id != explore.first().track.id,
            deepenTopId = deepen.firstOrNull()?.track?.id?.value,
            exploreTopId = explore.firstOrNull()?.track?.id?.value,
            artistDiversityAt5 = OfflineEvalMetrics.artistDiversityAtK(explore, 5),
            remasterBlocked = remasterRanked.none { it.track.id.value == "rem" },
            avgListeningProxy = OfflineEvalMetrics.averageListeningProxy(
                deepen, current, DiscoveryMode.Deepen,
            ),
            coldStartTopId = coldPick?.id?.value,
            warmTopId = warmPick?.id?.value,
            deepenSameArtistStreakRate = streakRate,
        )
        assertTrue(report.modeDivergence)
        assertTrue(report.artistDiversityAt5 >= 3)
        assertTrue(report.remasterBlocked)
        assertTrue(report.avgListeningProxy in 0.0..1.0)
        assertNotNull(report.coldStartTopId)
        assertNotNull(report.warmTopId)
        assertTrue(report.deepenSameArtistStreakRate in 0.0..1.0)
    }

    @Test
    fun `mmr improves explore diversity versus relevance-only`() {
        val current = track("cur", listOf("pop"), "a0")
        val pool = (1..10).map { i ->
            track(
                "t$i",
                listOf(if (i <= 4) "pop" else "indie"),
                if (i <= 3) "a0" else "a$i",
                popularity = 50 - i,
                features = AudioFeatures(0.5 + i * 0.02, 0.5, 0.5, 120.0),
            )
        }
        val ctx = RecommendationContext(
            currentTrack = current,
            recentInteractions = emptyList(),
            excludedIds = setOf(current.id),
            discoveryMode = DiscoveryMode.Explore,
            recentTracks = listOf(current),
        )
        val withMmr = ScoredHeuristicRecommendationEngine(useModePresets = true, useMmr = true)
            .rank(UserPreferenceProfile(), ctx, pool)
        val without = ScoredHeuristicRecommendationEngine(useModePresets = true, useMmr = false)
            .rank(UserPreferenceProfile(), ctx, pool)
        assertTrue(
            OfflineEvalMetrics.artistDiversityAtK(withMmr, 5) >=
                OfflineEvalMetrics.artistDiversityAtK(without, 5),
        )
    }

    @Test
    fun `transitionFit prefers continuity with recent window`() {
        val recent = listOf(
            track("r1", listOf("jazz"), "j1", features = AudioFeatures(0.4, 0.3, 0.5, 90.0)),
            track("r2", listOf("jazz"), "j2", features = AudioFeatures(0.42, 0.32, 0.48, 92.0)),
        )
        val near = track("near", listOf("jazz"), "j3", features = AudioFeatures(0.41, 0.31, 0.49, 91.0))
        val far = track("far", listOf("metal"), "m1", features = AudioFeatures(0.95, 0.9, 0.2, 160.0))
        val engineLocal = ScoredHeuristicRecommendationEngine(useModePresets = false, useMmr = false)
        val ctx = RecommendationContext(
            currentTrack = recent.last(),
            recentInteractions = emptyList(),
            excludedIds = emptySet(),
            recentTracks = recent,
            discoveryMode = DiscoveryMode.Normal,
        )
        val ranked = engineLocal.rank(
            UserPreferenceProfile(positiveSignalCount = 2),
            ctx,
            listOf(far, near),
        )
        assertEquals("near", ranked.first().track.id.value)
        assertTrue(ranked.first().breakdown.transitionFit >= ranked.last().breakdown.transitionFit)
    }
}
