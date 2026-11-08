package com.sensorial.data.spotify

import com.sensorial.domain.model.GenreId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpotifyTrackMapperTest {
    @Test
    fun mapsSpotifyTrackToDomain() {
        val dto = SpotifyTrackDto(
            id = "abc123",
            name = "Night Drive",
            artists = listOf(SpotifyArtistDto(id = "a1", name = "Nova")),
            album = SpotifyAlbumDto(
                id = "al1",
                name = "Neon",
                images = listOf(SpotifyImageDto(url = "https://example.com/art.jpg", width = 640)),
            ),
            durationMs = 210_000L,
            previewUrl = "https://example.com/preview.mp3",
        )
        val track = SpotifyTrackMapper.toDomain(
            dto,
            genres = listOf(GenreId("indie")),
            features = AudioFeatureFallback.forGenres(listOf(GenreId("indie"))),
        )
        assertEquals("abc123", track.id.value)
        assertEquals("Night Drive", track.title)
        assertEquals("Nova", track.primaryArtist.name)
        assertEquals("https://example.com/preview.mp3", track.previewUrl)
        assertEquals("https://example.com/art.jpg", track.artworkUrl)
        assertEquals(210_000L, track.durationMs)
    }

    @Test
    fun blankPreviewStaysNull() {
        val dto = SpotifyTrackDto(
            id = "x",
            name = "Song",
            artists = listOf(SpotifyArtistDto(id = "a", name = "A")),
            previewUrl = null,
        )
        val track = SpotifyTrackMapper.toDomain(dto, genres = listOf(GenreId("pop")))
        assertNull(track.previewUrl)
    }
}
