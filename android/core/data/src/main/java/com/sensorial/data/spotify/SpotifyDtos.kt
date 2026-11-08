package com.sensorial.data.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifySearchResponse(
    val tracks: SpotifyPagingTracks? = null,
)

@Serializable
data class SpotifyPagingTracks(
    val items: List<SpotifyTrackDto> = emptyList(),
)

@Serializable
data class SpotifyTracksResponse(
    val tracks: List<SpotifyTrackDto?> = emptyList(),
)

@Serializable
data class SpotifyTopTracksResponse(
    val tracks: List<SpotifyTrackDto> = emptyList(),
)

@Serializable
data class SpotifyTrackDto(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtistDto> = emptyList(),
    val album: SpotifyAlbumDto? = null,
    @SerialName("duration_ms") val durationMs: Long = 180_000L,
    @SerialName("preview_url") val previewUrl: String? = null,
    val popularity: Int = 0,
    val explicit: Boolean = false,
)

@Serializable
data class SpotifyArtistDto(
    val id: String,
    val name: String,
    val genres: List<String> = emptyList(),
)

@Serializable
data class SpotifyRelatedArtistsResponse(
    val artists: List<SpotifyArtistDto> = emptyList(),
)

@Serializable
data class SpotifyArtistsResponse(
    val artists: List<SpotifyArtistDto?> = emptyList(),
)

@Serializable
data class SpotifyAlbumsResponse(
    val items: List<SpotifyAlbumDto> = emptyList(),
)

@Serializable
data class SpotifyAlbumTracksResponse(
    val items: List<SpotifySimplifiedTrackDto> = emptyList(),
)

@Serializable
data class SpotifySimplifiedTrackDto(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtistDto> = emptyList(),
    @SerialName("duration_ms") val durationMs: Long = 180_000L,
    @SerialName("preview_url") val previewUrl: String? = null,
    val explicit: Boolean = false,
)

@Serializable
data class SpotifyAlbumDto(
    val id: String,
    val name: String,
    val images: List<SpotifyImageDto> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("album_type") val albumType: String? = null,
)

@Serializable
data class SpotifyImageDto(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class SpotifyAudioFeaturesDto(
    val id: String? = null,
    val energy: Double? = null,
    val danceability: Double? = null,
    val valence: Double? = null,
    val tempo: Double? = null,
)

@Serializable
data class SpotifyAudioFeaturesListDto(
    @SerialName("audio_features") val audioFeatures: List<SpotifyAudioFeaturesDto?> = emptyList(),
)
