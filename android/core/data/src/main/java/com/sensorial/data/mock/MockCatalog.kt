package com.sensorial.data.mock

import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId

object MockCatalog {

    fun tracks(): List<Track> = listOf(
        track("t01", "Neon Pulse", "Luna Vox", "Afterglow", "pop", 0.72, 0.80, 0.65, 122.0),
        track("t02", "City Lights", "Luna Vox", "Afterglow", "pop", 0.68, 0.75, 0.70, 118.0),
        track("t03", "Soft Static", "Mira Key", "Glass Room", "indie", 0.45, 0.55, 0.60, 104.0),
        track("t04", "Harbor Fog", "Mira Key", "Glass Room", "indie", 0.40, 0.48, 0.52, 98.0),
        track("t05", "Voltage", "Circuit Youth", "Gridlock", "electronic", 0.88, 0.90, 0.55, 128.0),
        track("t06", "Night Bus", "Circuit Youth", "Gridlock", "electronic", 0.82, 0.85, 0.48, 126.0),
        track("t07", "Redline", "Iron Grove", "Ash & Chrome", "rock", 0.85, 0.50, 0.40, 140.0),
        track("t08", "Broken Antenna", "Iron Grove", "Ash & Chrome", "rock", 0.78, 0.45, 0.35, 136.0),
        track("t09", "Dust Choir", "Cedar Saints", "Dry Season", "folk", 0.30, 0.35, 0.58, 92.0),
        track("t10", "Willow Road", "Cedar Saints", "Dry Season", "folk", 0.28, 0.32, 0.62, 88.0),
        track("t11", "Blue Smoke", "Nova Quartet", "Late Set", "jazz", 0.35, 0.40, 0.50, 110.0),
        track("t12", "Midnight Solo", "Nova Quartet", "Late Set", "jazz", 0.32, 0.38, 0.45, 108.0),
        track("t13", "Forge", "Black Orbit", "Gravity Well", "metal", 0.95, 0.42, 0.25, 160.0),
        track("t14", "Impact Crater", "Black Orbit", "Gravity Well", "metal", 0.92, 0.40, 0.22, 155.0),
        track("t15", "Palm Frequency", "Solara", "Coastal AM", "latin", 0.70, 0.88, 0.82, 100.0),
        track("t16", "Sunset Taxi", "Solara", "Coastal AM", "latin", 0.66, 0.84, 0.78, 98.0),
        track("t17", "Paper Planes", "Echo Park", "Demo Tapes", "hiphop", 0.60, 0.78, 0.55, 94.0),
        track("t18", "Sidewalk Cipher", "Echo Park", "Demo Tapes", "hiphop", 0.58, 0.80, 0.50, 96.0),
        track("t19", "Low Tide", "Aeon Room", "Depths", "ambient", 0.15, 0.20, 0.40, 70.0),
        track("t20", "Glass Ocean", "Aeon Room", "Depths", "ambient", 0.12, 0.18, 0.42, 68.0),
        track("t21", "Golden Hour", "Luna Vox", "Singles", "pop", 0.55, 0.70, 0.85, 112.0),
        track("t22", "Pixel Heart", "Circuit Youth", "Singles", "electronic", 0.75, 0.86, 0.72, 124.0),
        track("t23", "Cabin Fire", "Cedar Saints", "Singles", "folk", 0.33, 0.30, 0.70, 90.0),
        track("t24", "Astrolabe", "Aeon Room", "Singles", "ambient", 0.20, 0.22, 0.48, 72.0),
        track("t25", "Concrete Bloom", "Echo Park", "Singles", "hiphop", 0.62, 0.76, 0.60, 97.0),
        track("t26", "Signal Loss", "Iron Grove", "Singles", "rock", 0.80, 0.48, 0.38, 138.0),
        track("t27", "Samba Drift", "Solara", "Singles", "latin", 0.68, 0.90, 0.88, 102.0),
        track("t28", "Velvet Rain", "Nova Quartet", "Singles", "jazz", 0.38, 0.42, 0.55, 106.0),
        track("t29", "Overdrive", "Black Orbit", "Singles", "metal", 0.97, 0.44, 0.28, 170.0),
        track("t30", "Window Seat", "Mira Key", "Singles", "indie", 0.42, 0.50, 0.66, 100.0),
    )

    private fun track(
        id: String,
        title: String,
        artistName: String,
        albumName: String,
        genre: String,
        energy: Double,
        danceability: Double,
        valence: Double,
        tempo: Double,
    ): Track {
        val artistId = ArtistId(artistName.lowercase().replace(' ', '-'))
        return Track(
            id = TrackId(id),
            title = title,
            artists = listOf(Artist(artistId, artistName)),
            album = Album(
                id = albumName.lowercase().replace(' ', '-'),
                name = albumName,
                artworkUrl = "https://picsum.photos/seed/$id/600",
            ),
            genres = listOf(GenreId(genre)),
            audioFeatures = AudioFeatures(energy, danceability, valence, tempo),
            artworkUrl = "https://picsum.photos/seed/$id/600",
            durationMs = 180_000L + (id.filter { it.isDigit() }.toIntOrNull() ?: 0) * 1_000L,
        )
    }
}
