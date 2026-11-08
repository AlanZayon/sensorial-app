package com.sensorial.data.spotify

import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.GenreId

/**
 * Genre-heuristic audio features used when Spotify `/v1/audio-features` returns 403/404
 * (restricted for many apps since Nov 2024).
 */
object AudioFeatureFallback {
    fun forGenres(genres: List<GenreId>): AudioFeatures {
        val keys = genres.map { it.value.lowercase() }
        return when {
            keys.any { it.contains("metal") || it.contains("punk") } ->
                AudioFeatures(0.85, 0.45, 0.35, 140.0)
            keys.any { it.contains("electronic") || it.contains("dance") || it.contains("edm") || it.contains("house") } ->
                AudioFeatures(0.80, 0.85, 0.55, 128.0)
            keys.any { it.contains("hip") || it.contains("rap") } ->
                AudioFeatures(0.70, 0.75, 0.45, 95.0)
            keys.any { it.contains("jazz") } ->
                AudioFeatures(0.40, 0.35, 0.50, 110.0)
            keys.any { it.contains("ambient") || it.contains("classical") } ->
                AudioFeatures(0.25, 0.20, 0.40, 80.0)
            keys.any { it.contains("folk") || it.contains("acoustic") || it.contains("singer") } ->
                AudioFeatures(0.35, 0.40, 0.55, 105.0)
            keys.any { it.contains("latin") || it.contains("reggaeton") || it.contains("samba") } ->
                AudioFeatures(0.75, 0.80, 0.70, 100.0)
            keys.any { it.contains("rock") } ->
                AudioFeatures(0.75, 0.55, 0.50, 125.0)
            keys.any { it.contains("indie") || it.contains("alternative") } ->
                AudioFeatures(0.55, 0.50, 0.45, 118.0)
            keys.any { it.contains("pop") } ->
                AudioFeatures(0.65, 0.70, 0.60, 120.0)
            else ->
                AudioFeatures(0.55, 0.55, 0.50, 118.0)
        }
    }

    fun fromApiOrFallback(
        energy: Double?,
        danceability: Double?,
        valence: Double?,
        tempo: Double?,
        genres: List<GenreId>,
    ): AudioFeatures {
        if (energy != null && danceability != null && valence != null && tempo != null && tempo > 0) {
            return AudioFeatures(
                energy = energy.coerceIn(0.0, 1.0),
                danceability = danceability.coerceIn(0.0, 1.0),
                valence = valence.coerceIn(0.0, 1.0),
                tempo = tempo,
            )
        }
        return forGenres(genres)
    }
}
