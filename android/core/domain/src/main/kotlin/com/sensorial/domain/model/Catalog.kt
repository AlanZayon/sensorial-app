package com.sensorial.domain.model

data class Artist(
    val id: ArtistId,
    val name: String,
)

data class Album(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    /** Four-digit release year when known (Spotify album.release_date). */
    val releaseYear: Int? = null,
)

/**
 * Normalized audio characteristics in the 0.0..1.0 range (except tempo in BPM).
 * Values are provider-agnostic; Spotify mapping happens in the data layer later.
 */
data class AudioFeatures(
    val energy: Double,
    val danceability: Double,
    val valence: Double,
    val tempo: Double,
) {
    init {
        require(energy in 0.0..1.0) { "energy must be in 0..1" }
        require(danceability in 0.0..1.0) { "danceability must be in 0..1" }
        require(valence in 0.0..1.0) { "valence must be in 0..1" }
        require(tempo > 0.0) { "tempo must be positive" }
    }
}

data class Track(
    val id: TrackId,
    val title: String,
    val artists: List<Artist>,
    val album: Album,
    val genres: List<GenreId>,
    val audioFeatures: AudioFeatures,
    val artworkUrl: String? = album.artworkUrl,
    val durationMs: Long = 180_000L,
    /** 30s preview URL when App Remote is unavailable (Spotify Web API). */
    val previewUrl: String? = null,
    /** Spotify popularity 0..100 when known. */
    val popularity: Int = 0,
    /** 1.0 = real audio-features; lower = genre heuristic fallback. */
    val featureConfidence: Double = 1.0,
    val explicit: Boolean = false,
) {
    init {
        require(title.isNotBlank()) { "title cannot be blank" }
        require(artists.isNotEmpty()) { "track must have at least one artist" }
        require(featureConfidence in 0.0..1.0) { "featureConfidence must be in 0..1" }
        require(popularity in 0..100) { "popularity must be in 0..100" }
    }

    val primaryArtist: Artist get() = artists.first()

    val releaseYear: Int? get() = album.releaseYear
}
