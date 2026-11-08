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
import kotlin.test.assertTrue

/**
 * Offline evaluation fixtures — guards regression of mode behavior, diversity, and remaster blocking.
 */
class OfflineEvalHarnessTest {
    private val engine = ScoredHeuristicRecommendationEngine(useModePresets = true)

    private fun track(
        id: String,
        genres: List<String>,
        artist: String,
        popularity: Int = 50,
        year: Int? = 2020,
        features: AudioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
    ) = Track(
        id = TrackId(id),
        title = id,
        artists = listOf(Artist(ArtistId(artist), artist)),
        album = Album("al-$id", "Album", releaseYear = year),
        genres = genres.map(::GenreId),
        audioFeatures = features,
        popularity = popularity,
    )

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
        val distinctArtists = ranked.map { it.track.primaryArtist.id.value }.toSet().size
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
}
