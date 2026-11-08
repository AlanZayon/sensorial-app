package com.sensorial.data.spotify

import com.sensorial.domain.model.GenreId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioFeatureFallbackTest {
    @Test
    fun metalIsHighEnergy() {
        val features = AudioFeatureFallback.forGenres(listOf(GenreId("metal")))
        assertTrue(features.energy > 0.7)
        assertTrue(features.tempo >= 130.0)
    }

    @Test
    fun ambientIsLowEnergy() {
        val features = AudioFeatureFallback.forGenres(listOf(GenreId("ambient")))
        assertTrue(features.energy < 0.4)
    }

    @Test
    fun apiValuesPreferredWhenPresent() {
        val features = AudioFeatureFallback.fromApiOrFallback(
            energy = 0.2,
            danceability = 0.3,
            valence = 0.4,
            tempo = 90.0,
            genres = listOf(GenreId("metal")),
        )
        assertEquals(0.2, features.energy, 1e-9)
        assertEquals(90.0, features.tempo, 1e-9)
    }

    @Test
    fun missingApiFallsBackToGenre() {
        val features = AudioFeatureFallback.fromApiOrFallback(
            energy = null,
            danceability = null,
            valence = null,
            tempo = null,
            genres = listOf(GenreId("pop")),
        )
        assertTrue(features.energy in 0.0..1.0)
        assertTrue(features.tempo > 0.0)
    }
}
