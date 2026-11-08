package com.sensorial.testing

import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId

object TrackFixtures {
    fun track(
        id: String,
        genre: String = "pop",
        artist: String = "artist-$id",
        features: AudioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
    ): Track = Track(
        id = TrackId(id),
        title = "Track $id",
        artists = listOf(Artist(ArtistId(artist), artist)),
        album = Album("album-$id", "Album $id"),
        genres = listOf(GenreId(genre)),
        audioFeatures = features,
    )
}
