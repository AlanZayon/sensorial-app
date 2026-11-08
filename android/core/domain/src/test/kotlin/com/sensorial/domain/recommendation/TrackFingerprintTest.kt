package com.sensorial.domain.recommendation

import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackFingerprintTest {
    private fun track(id: String, title: String, artist: String = "Radiohead") = Track(
        id = TrackId(id),
        title = title,
        artists = listOf(Artist(ArtistId("radiohead"), artist)),
        album = Album("al-$id", "Album"),
        genres = listOf(GenreId("rock")),
        audioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
    )

    @Test
    fun `remasters share fingerprint with original`() {
        val original = track("1", "Creep")
        val remaster = track("2", "Creep - Remastered 2017")
        val live = track("3", "Creep (Live)")
        assertTrue(TrackFingerprint.isSameWork(original, remaster))
        assertTrue(TrackFingerprint.isSameWork(original, live))
        assertEquals(TrackFingerprint.of(original), TrackFingerprint.of(remaster))
    }

    @Test
    fun `different songs stay distinct`() {
        val a = track("1", "Creep")
        val b = track("2", "Karma Police")
        assertFalse(TrackFingerprint.isSameWork(a, b))
    }

    @Test
    fun `version block rejects alternate ids of same work`() {
        val recent = listOf(track("1", "Creep"))
        val remaster = track("2", "Creep (Remastered)")
        assertTrue(RecentVersionBlock.isBlocked(remaster, recent, currentTrack = recent.first()))
        assertFalse(
            RecentVersionBlock.isBlocked(
                track("3", "Fake Plastic Trees"),
                recent,
                currentTrack = recent.first(),
            ),
        )
    }

    @Test
    fun `recent track penalty hits remaster of heard song`() {
        val recent = listOf(track("1", "Creep"), track("x", "Other", artist = "Other"))
        val remaster = track("2", "Creep - Remastered")
        val penalty = RecentTrackPenalty.score(remaster, recent)
        assertTrue(penalty > 0.0)
    }
}
