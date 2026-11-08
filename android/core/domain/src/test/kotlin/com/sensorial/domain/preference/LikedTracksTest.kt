package com.sensorial.domain.preference

import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.InteractionId
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction
import kotlin.test.Test
import kotlin.test.assertEquals

class LikedTracksTest {
    private fun like(id: String, at: Long, type: InteractionType = InteractionType.ExplicitLike) =
        TrackInteraction(
            id = InteractionId("$type-$id-$at"),
            trackId = TrackId(id),
            interactionType = type,
            listeningDurationMs = 0,
            timestampEpochMs = at,
        )

    private fun track(
        id: String,
        title: String,
        artist: String,
        genre: String,
    ) = Track(
        id = TrackId(id),
        title = title,
        artists = listOf(Artist(ArtistId(artist), artist)),
        album = Album("al-$id", "Album $id"),
        genres = listOf(GenreId(genre)),
        audioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
    )

    @Test
    fun `folds likes and unlikes newest first`() {
        val refs = LikedTracks.current(
            listOf(
                like("a", 1),
                like("b", 2),
                like("a", 3, InteractionType.ExplicitUnlike),
                like("c", 4),
                like("b", 5),
            ),
        )
        assertEquals(listOf("b", "c"), refs.map { it.trackId.value })
        assertEquals(5L, refs.first().likedAtEpochMs)
    }

    @Test
    fun `filter matches query artist and genre`() {
        val tracks = listOf(
            track("1", "Neon Pulse", "Nova", "indie"),
            track("2", "Deep House", "Nova", "electronic"),
            track("3", "Samba", "Rio", "samba"),
        )
        assertEquals(
            listOf("1"),
            LikedTracks.filter(tracks, query = "neon").map { it.id.value },
        )
        assertEquals(
            listOf("1", "2"),
            LikedTracks.filter(tracks, artistId = ArtistId("Nova")).map { it.id.value },
        )
        assertEquals(
            listOf("3"),
            LikedTracks.filter(tracks, genreId = GenreId("samba")).map { it.id.value },
        )
        assertEquals(
            listOf("2"),
            LikedTracks.filter(tracks, query = "electronic").map { it.id.value },
        )
    }

    @Test
    fun `sort by title and artist`() {
        val tracks = listOf(
            track("1", "Zebra", "Beta", "indie"),
            track("2", "Apple", "Alpha", "pop"),
            track("3", "Moon", "Alpha", "jazz"),
        )
        val likedAt = mapOf(
            TrackId("1") to 30L,
            TrackId("2") to 10L,
            TrackId("3") to 20L,
        )
        assertEquals(
            listOf("1", "3", "2"),
            LikedTracks.filter(tracks, sort = LikedSort.Recent, likedAtById = likedAt).map { it.id.value },
        )
        assertEquals(
            listOf("2", "3", "1"),
            LikedTracks.filter(tracks, sort = LikedSort.Title, likedAtById = likedAt).map { it.id.value },
        )
        assertEquals(
            listOf("2", "3", "1"),
            LikedTracks.filter(tracks, sort = LikedSort.Artist, likedAtById = likedAt).map { it.id.value },
        )
    }

    @Test
    fun `genre label is human readable`() {
        assertEquals("Indie Rock", LikedTracks.genreLabel(GenreId("indie-rock")))
        assertEquals("Pop", LikedTracks.genreLabel(GenreId("pop")))
    }
}
