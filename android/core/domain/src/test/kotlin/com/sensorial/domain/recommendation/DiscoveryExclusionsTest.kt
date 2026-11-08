package com.sensorial.domain.recommendation

import com.sensorial.domain.history.HistoryEntry
import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.InteractionId
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoveryExclusionsTest {
    private fun track(id: String, title: String = id, artistId: String = "artist-a") = Track(
        id = TrackId(id),
        title = title,
        artists = listOf(Artist(ArtistId(artistId), "Artist")),
        album = Album("al-$id", "Album"),
        genres = emptyList(),
        audioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
    )

    private fun interaction(
        id: String,
        progress: Double,
        type: InteractionType = InteractionType.NavigationAdvance,
    ) = TrackInteraction(
        id = InteractionId("i-$id-$progress"),
        trackId = TrackId(id),
        interactionType = type,
        listeningDurationMs = 10_000,
        timestampEpochMs = 1,
        listeningProgress = progress,
    )

    @Test
    fun `liked and well-heard tracks are excluded`() {
        val excluded = DiscoveryExclusions.ids(
            likedIds = setOf(TrackId("liked")),
            interactions = listOf(
                interaction("heard", 0.8),
                interaction("skip", 0.05),
            ),
            historyEntries = listOf(HistoryEntry(track("hist"))),
        )
        assertTrue(TrackId("liked") in excluded)
        assertTrue(TrackId("heard") in excluded)
        assertTrue(TrackId("hist") in excluded)
        assertFalse(TrackId("skip") in excluded)
    }

    @Test
    fun `fingerprints block remasters of liked works`() {
        val original = track("1", "Neon Pulse")
        val remaster = track("2", "Neon Pulse - Remastered")
        val fps = DiscoveryExclusions.fingerprints(
            knownTracks = listOf(original),
            likedIds = setOf(TrackId("1")),
            heardIds = emptySet(),
        )
        assertTrue(TrackFingerprint.of(original) in fps)
        assertTrue(TrackFingerprint.of(remaster) in fps)
    }
}
