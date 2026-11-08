package com.sensorial.data.spotify

import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId

object SpotifyTrackMapper {
    fun toDomain(
        dto: SpotifyTrackDto,
        genres: List<GenreId> = emptyList(),
        features: AudioFeatures? = null,
        featureConfidence: Double = if (features != null) 1.0 else 0.35,
    ): Track {
        require(dto.id.isNotBlank()) { "Spotify track id blank" }
        require(dto.name.isNotBlank()) { "Spotify track name blank" }
        val artists = dto.artists
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .map { Artist(ArtistId(it.id), it.name) }
            .ifEmpty { listOf(Artist(ArtistId("unknown"), "Unknown Artist")) }
        val artwork = dto.album?.images
            ?.sortedByDescending { it.width ?: 0 }
            ?.firstOrNull()
            ?.url
            ?.takeIf { it.isNotBlank() }
        val releaseYear = dto.album?.releaseDate
            ?.take(4)
            ?.toIntOrNull()
            ?.takeIf { it in 1900..2100 }
        val album = Album(
            id = dto.album?.id?.takeIf { it.isNotBlank() } ?: "album-${dto.id}",
            name = dto.album?.name?.takeIf { it.isNotBlank() } ?: "Unknown Album",
            artworkUrl = artwork,
            releaseYear = releaseYear,
        )
        val resolvedGenres = genres.ifEmpty { listOf(GenreId("unknown")) }
        return Track(
            id = TrackId(dto.id),
            title = dto.name,
            artists = artists,
            album = album,
            genres = resolvedGenres,
            audioFeatures = features ?: AudioFeatureFallback.forGenres(resolvedGenres),
            artworkUrl = artwork,
            durationMs = dto.durationMs.takeIf { it > 0 } ?: 180_000L,
            previewUrl = dto.previewUrl,
            popularity = dto.popularity.coerceIn(0, 100),
            featureConfidence = featureConfidence.coerceIn(0.0, 1.0),
            explicit = dto.explicit,
        )
    }

    fun releaseYearFrom(date: String?): Int? =
        date?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2100 }
}
