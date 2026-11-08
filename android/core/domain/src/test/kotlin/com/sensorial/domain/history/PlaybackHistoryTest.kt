package com.sensorial.domain.history

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
import kotlin.test.assertFailsWith

class PlaybackHistoryTest {

    private fun track(id: String) = Track(
        id = TrackId(id),
        title = "Track $id",
        artists = listOf(Artist(ArtistId("a1"), "Artist")),
        album = Album("al1", "Album"),
        genres = listOf(GenreId("pop")),
        audioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
    )

    @Test
    fun `push sets current track`() {
        val history = PlaybackHistory()
        history.push(track("A"))
        assertEquals("A", history.currentTrack?.id?.value)
        assertFalse(history.canGoBack)
        assertFalse(history.canGoForward)
    }

    @Test
    fun `previous and next navigate without losing stack`() {
        val history = PlaybackHistory()
        history.push(track("A"))
        history.push(track("B"))
        history.push(track("C"))
        history.push(track("D"))

        assertEquals("D", history.currentTrack?.id?.value)
        assertEquals("C", history.previous().id.value)
        assertEquals("B", history.previous().id.value)
        assertTrue(history.canGoForward)
        assertEquals("C", history.next().id.value)
        assertEquals("D", history.next().id.value)
    }

    @Test
    fun `push after going back truncates forward`() {
        val history = PlaybackHistory()
        history.push(track("A"))
        history.push(track("B"))
        history.push(track("C"))
        history.previous()
        history.previous()
        history.push(track("X"))

        assertEquals(listOf("A", "X"), history.snapshot().map { it.id.value })
        assertEquals("X", history.currentTrack?.id?.value)
        assertFalse(history.canGoForward)
        assertTrue(history.canGoBack)
    }

    @Test
    fun `respects max size`() {
        val history = PlaybackHistory(HistoryConfig(maxSize = 3))
        history.push(track("A"))
        history.push(track("B"))
        history.push(track("C"))
        history.push(track("D"))

        assertEquals(3, history.size)
        assertEquals(listOf("B", "C", "D"), history.snapshot().map { it.id.value })
        assertEquals("D", history.currentTrack?.id?.value)
    }

    @Test
    fun `previous fails at start`() {
        val history = PlaybackHistory()
        history.push(track("A"))
        assertFailsWith<IllegalStateException> { history.previous() }
    }
}
