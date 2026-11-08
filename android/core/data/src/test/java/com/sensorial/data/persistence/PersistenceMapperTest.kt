package com.sensorial.data.persistence

import com.sensorial.data.persistence.mapper.decodePreferenceJson
import com.sensorial.data.persistence.mapper.decodeSettingsJson
import com.sensorial.data.persistence.mapper.decodeTrackJson
import com.sensorial.data.persistence.mapper.encodeJson
import com.sensorial.domain.history.HistoryConfig
import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.preference.UserPreferenceProfile
import com.sensorial.domain.recommendation.RecommendationWeights
import com.sensorial.domain.repository.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistenceMapperTest {

    @Test
    fun `track round-trips through json`() {
        val track = Track(
            id = TrackId("t01"),
            title = "Neon Pulse",
            artists = listOf(Artist(ArtistId("luna"), "Luna Vox")),
            album = Album("afterglow", "Afterglow", "https://example.com/a.png"),
            genres = listOf(GenreId("pop")),
            audioFeatures = AudioFeatures(0.72, 0.8, 0.65, 122.0),
            artworkUrl = "https://example.com/a.png",
            durationMs = 190_000,
        )
        val restored = decodeTrackJson(track.encodeJson())
        assertEquals(track, restored)
    }

    @Test
    fun `preference profile round-trips through json`() {
        val profile = UserPreferenceProfile(
            genreScores = mapOf(GenreId("pop") to 0.8),
            artistScores = mapOf(ArtistId("luna") to 0.4),
            preferredFeatures = AudioFeatures(0.7, 0.7, 0.6, 120.0),
            explorationPreference = 0.3,
            positiveSignalCount = 2,
            negativeSignalCount = 1,
        )
        val restored = decodePreferenceJson(profile.encodeJson())
        assertEquals(profile, restored)
    }

    @Test
    fun `settings round-trips through json`() {
        val settings = AppSettings(
            historyConfig = HistoryConfig(maxSize = 40),
            recommendationWeights = RecommendationWeights(genre = 1.2, exploration = 0.4),
        )
        val restored = decodeSettingsJson(settings.encodeJson())
        assertEquals(settings, restored)
    }
}
