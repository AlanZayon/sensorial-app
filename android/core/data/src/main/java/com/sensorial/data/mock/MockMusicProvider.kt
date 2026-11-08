package com.sensorial.data.mock

import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.provider.MusicProvider
import com.sensorial.domain.recommendation.RecommendationContext
import kotlinx.coroutines.delay

class MockMusicProvider(
    private val catalog: List<Track> = MockCatalog.tracks(),
    private val artificialLatencyMs: Long = 0,
) : MusicProvider {

    override suspend fun searchTracks(query: String): List<Track> {
        maybeDelay()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return catalog
        return catalog.filter { track ->
            track.title.lowercase().contains(q) ||
                track.artists.any { it.name.lowercase().contains(q) } ||
                track.genres.any { it.value.lowercase().contains(q) }
        }
    }

    override suspend fun getTrack(id: TrackId): Track? {
        maybeDelay()
        return catalog.firstOrNull { it.id == id }
    }

    override suspend fun getCandidates(context: RecommendationContext): List<Track> {
        maybeDelay()
        return catalog
    }

    override suspend fun playTrack(id: TrackId) {
        maybeDelay()
        // Phase 1: fake playback hook — real Spotify remote/SDK arrives in Phase 5.
    }

    private suspend fun maybeDelay() {
        if (artificialLatencyMs > 0) delay(artificialLatencyMs)
    }
}
