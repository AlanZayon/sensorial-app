package com.sensorial.data.spotify

import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.recommendation.RecentVersionBlock
import com.sensorial.domain.recommendation.TrackFingerprint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Mirrors [CandidateQueryBuilder] pool assembly rules: id exclusion + fingerprint dedupe.
 */
class CandidateExclusionTest {
    @Test
    fun excludesAndDedupesByFingerprint() {
        val original = track("a", "Creep")
        val remaster = track("b", "Creep - Remastered")
        val other = track("c", "Karma Police")
        val excluded = setOf(TrackId("a"))
        val blocked = RecentVersionBlock.fingerprints(recentTracks = listOf(original), currentTrack = original)
        val collected = LinkedHashMap<String, Track>()
        fun addAll(tracks: List<Track>) {
            for (track in tracks) {
                if (track.id in excluded) continue
                val fingerprint = TrackFingerprint.of(track)
                if (fingerprint in blocked) continue
                if (fingerprint in collected) continue
                collected[fingerprint] = track
            }
        }
        addAll(listOf(original, remaster, other, remaster))
        assertEquals(1, collected.size)
        assertEquals("c", collected.values.first().id.value)
        assertFalse(TrackFingerprint.of(remaster) in collected)
        assertTrue(TrackFingerprint.of(other) in collected)
    }

    private fun track(id: String, title: String) = Track(
        id = TrackId(id),
        title = title,
        artists = listOf(Artist(ArtistId("radiohead"), "Radiohead")),
        album = Album("al", "Album"),
        genres = listOf(GenreId("rock")),
        audioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
    )
}
