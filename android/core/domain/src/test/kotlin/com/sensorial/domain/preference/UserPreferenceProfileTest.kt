package com.sensorial.domain.preference

import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.GestureIntensity
import com.sensorial.domain.model.HorizontalIntent
import com.sensorial.domain.model.InteractionId
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction
import com.sensorial.domain.model.VerticalIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserPreferenceProfileTest {

    private val track = Track(
        id = TrackId("t1"),
        title = "Neon",
        artists = listOf(Artist(ArtistId("luna"), "Luna")),
        album = Album("al", "Album"),
        genres = listOf(GenreId("pop")),
        audioFeatures = AudioFeatures(0.7, 0.8, 0.6, 120.0),
    )

    private fun interaction(
        vertical: VerticalIntent = VerticalIntent.None,
        horizontal: HorizontalIntent = HorizontalIntent.None,
        intensity: GestureIntensity = GestureIntensity.Medium,
        velocity: Double = 0.5,
        listeningMs: Long = 10_000,
    ) = TrackInteraction(
        id = InteractionId("i1"),
        trackId = track.id,
        interactionType = InteractionType.Preference,
        verticalIntent = vertical,
        horizontalIntent = horizontal,
        gestureIntensity = intensity,
        gestureVelocity = velocity,
        listeningDurationMs = listeningMs,
        timestampEpochMs = 1L,
    )

    @Test
    fun `high intensity and velocity increase positive genre delta`() {
        val weak = UserPreferenceProfile().applying(
            track,
            interaction(
                vertical = VerticalIntent.Positive,
                intensity = GestureIntensity.Low,
                velocity = 0.1,
                listeningMs = 2_000,
            ).copy(interactionType = InteractionType.ExplicitLike),
        )
        val strong = UserPreferenceProfile().applying(
            track,
            interaction(
                vertical = VerticalIntent.Positive,
                intensity = GestureIntensity.High,
                velocity = 1.0,
                listeningMs = 25_000,
            ).copy(interactionType = InteractionType.ExplicitLike),
        )
        assertTrue(strong.genreScore(GenreId("pop")) > weak.genreScore(GenreId("pop")))
    }

    @Test
    fun `unlike reverses explicit like genre boost`() {
        val liked = UserPreferenceProfile().applying(
            track,
            interaction(
                intensity = GestureIntensity.High,
                velocity = 1.0,
                listeningMs = 25_000,
            ).copy(interactionType = InteractionType.ExplicitLike),
        )
        val unliked = liked.applying(
            track,
            interaction(
                intensity = GestureIntensity.High,
                velocity = 1.0,
                listeningMs = 25_000,
            ).copy(interactionType = InteractionType.ExplicitUnlike),
        )
        assertTrue(liked.genreScore(GenreId("pop")) > 0.0)
        assertEquals(0, unliked.positiveSignalCount)
        assertTrue(unliked.genreScore(GenreId("pop")) < liked.genreScore(GenreId("pop")))
    }

    @Test
    fun `listening without like raises listening score`() {
        val updated = UserPreferenceProfile().applying(
            track,
            TrackInteraction(
                id = InteractionId("nav"),
                trackId = track.id,
                interactionType = InteractionType.NavigationAdvance,
                listeningDurationMs = 90_000,
                timestampEpochMs = 1,
                listeningProgress = 0.9,
                localHour = 9,
            ),
        )
        assertEquals(0, updated.positiveSignalCount)
        assertTrue(updated.genreListeningScore(GenreId("pop")) > 0.0)
        assertTrue((updated.timeBucketSampleCounts[2] ?: 0) >= 1)
    }

    @Test
    fun `quick skip leaves durable aversion on genre and artist`() {
        val updated = UserPreferenceProfile().applying(
            track,
            TrackInteraction(
                id = InteractionId("skip"),
                trackId = track.id,
                interactionType = InteractionType.NavigationAdvance,
                listeningDurationMs = 2_000,
                timestampEpochMs = 1,
                listeningProgress = 0.08,
                artistId = track.primaryArtist.id,
            ),
        )
        assertTrue(updated.genreListeningScore(GenreId("pop")) < 0.0)
        assertTrue(updated.artistListeningScore(track.primaryArtist.id) < 0.0)
        assertTrue(updated.genreScore(GenreId("pop")) < 0.0)
        assertTrue(updated.artistScore(track.primaryArtist.id) < 0.0)
    }

    @Test
    fun `informed rejection strengthens negative genre score`() {
        val mild = UserPreferenceProfile().applying(
            track,
            interaction(
                vertical = VerticalIntent.Negative,
                intensity = GestureIntensity.Low,
                velocity = 0.2,
                listeningMs = 1_000,
            ),
        )
        val informed = UserPreferenceProfile().applying(
            track,
            interaction(
                vertical = VerticalIntent.Negative,
                intensity = GestureIntensity.High,
                velocity = 1.0,
                listeningMs = 25_000,
            ),
        )
        assertTrue(informed.genreScore(GenreId("pop")) < mild.genreScore(GenreId("pop")))
    }

    @Test
    fun `explore increases exploration preference`() {
        val updated = UserPreferenceProfile(explorationPreference = 0.4).applying(
            track,
            interaction(horizontal = HorizontalIntent.Explore, intensity = GestureIntensity.High, velocity = 0.9),
        )
        assertTrue(updated.explorationPreference > 0.4)
    }

    @Test
    fun `similar decreases exploration preference`() {
        val updated = UserPreferenceProfile(explorationPreference = 0.6).applying(
            track,
            interaction(horizontal = HorizontalIntent.Similar, intensity = GestureIntensity.High, velocity = 0.9),
        )
        assertTrue(updated.explorationPreference < 0.6)
    }

    @Test
    fun `topPositiveGenres orders by score and skips negatives`() {
        val profile = UserPreferenceProfile(
            genreScores = mapOf(
                GenreId("pop") to 0.9,
                GenreId("metal") to -0.5,
                GenreId("indie") to 0.4,
            ),
        )
        assertEquals(listOf(GenreId("pop"), GenreId("indie")), profile.topPositiveGenres(limit = 3))
    }
}
