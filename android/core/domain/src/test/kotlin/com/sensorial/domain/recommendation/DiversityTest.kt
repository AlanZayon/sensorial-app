package com.sensorial.domain.recommendation

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiversityTest {
    private fun track(id: String, artist: String, genre: String = "pop") = Track(
        id = TrackId(id),
        title = id,
        artists = listOf(Artist(ArtistId(artist), artist)),
        album = Album("al-$id", "Album"),
        genres = listOf(GenreId(genre)),
        audioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
    )

    @Test
    fun `same artist streak counts trailing shared primary`() {
        val a1 = track("1", "A")
        val a2 = track("2", "A")
        val b = track("3", "B")
        val a3 = track("4", "A")
        assertEquals(2, SameArtistStreak.count(ArtistId("A"), listOf(b, a1, a2)))
        assertEquals(1, SameArtistStreak.count(ArtistId("A"), listOf(a1, b, a3)))
        assertEquals(0, SameArtistStreak.count(ArtistId("A"), listOf(b)))
    }

    @Test
    fun `recent artist receives penalty`() {
        val recent = listOf(track("1", "A"), track("2", "A"), track("3", "B"))
        val penaltyA = RecentArtistPenalty.score(track("4", "A"), recent)
        val penaltyC = RecentArtistPenalty.score(track("5", "C"), recent)
        assertTrue(penaltyA > penaltyC)
    }

    @Test
    fun `recent track receives penalty`() {
        val recent = listOf(track("1", "A"), track("2", "B"), track("1", "A"))
        val same = RecentTrackPenalty.score(track("1", "A"), recent)
        val fresh = RecentTrackPenalty.score(track("9", "Z"), recent)
        assertTrue(same > fresh)
        assertEquals(0.0, fresh)
    }

    @Test
    fun `version of recent title is blocked`() {
        fun titled(id: String, title: String, artist: String) = Track(
            id = TrackId(id),
            title = title,
            artists = listOf(Artist(ArtistId(artist), artist)),
            album = Album("al-$id", "Album"),
            genres = listOf(GenreId("pop")),
            audioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
        )
        val recent = listOf(titled("1", "Neon Lights", "A"))
        val remaster = titled("2", "Neon Lights - Remastered", "A")
        assertTrue(RecentVersionBlock.isBlocked(remaster, recent))
    }

    @Test
    fun `single quick skip does not activate artist focus`() {
        val t = track("1", "A")
        val interactions = listOf(
            TrackInteraction(
                id = InteractionId("i1"),
                trackId = t.id,
                interactionType = InteractionType.NavigationAdvance,
                listeningDurationMs = 1_000,
                timestampEpochMs = 1,
                listeningProgress = 0.05,
                artistId = t.primaryArtist.id,
            ),
        )
        val focus = ArtistFocusDetector.detect(interactions, mapOf(t.id to t))
        assertNull(focus.focusedArtistId)
    }

    @Test
    fun `multi signal activates artist focus`() {
        val a1 = track("1", "A")
        val a2 = track("2", "A")
        val interactions = listOf(
            TrackInteraction(
                id = InteractionId("i1"),
                trackId = a1.id,
                interactionType = InteractionType.ExplicitLike,
                listeningDurationMs = 90_000,
                timestampEpochMs = 1,
                listeningProgress = 0.9,
                artistId = a1.primaryArtist.id,
            ),
            TrackInteraction(
                id = InteractionId("i2"),
                trackId = a2.id,
                interactionType = InteractionType.NavigationReturn,
                listeningDurationMs = 40_000,
                timestampEpochMs = 2,
                listeningProgress = 0.7,
                artistId = a2.primaryArtist.id,
            ),
            TrackInteraction(
                id = InteractionId("i3"),
                trackId = a2.id,
                interactionType = InteractionType.NavigationAdvance,
                listeningDurationMs = 80_000,
                timestampEpochMs = 3,
                listeningProgress = 0.8,
                artistId = a2.primaryArtist.id,
            ),
        )
        val focus = ArtistFocusDetector.detect(
            interactions,
            mapOf(a1.id to a1, a2.id to a2),
        )
        assertNotNull(focus.focusedArtistId)
        assertEquals("A", focus.focusedArtistId!!.value)
    }
}
