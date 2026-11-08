package com.sensorial.domain.provider

import com.sensorial.domain.feed.PlaybackMode
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.recommendation.CandidatePool
import com.sensorial.domain.recommendation.RecommendationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Playback-side notifications from the music provider (App Remote / preview player). */
sealed class PlaybackEvent {
    /** Provider is now playing/showing this track (may differ from session current). */
    data class TrackActive(val trackId: TrackId) : PlaybackEvent()

    /** Current item finished (mainly preview); session may auto-advance. */
    data object TrackEnded : PlaybackEvent()

    data class IsPaused(val paused: Boolean) : PlaybackEvent()
}

interface MusicProvider {
    suspend fun searchTracks(query: String): List<Track>
    suspend fun getTrack(id: TrackId): Track?

    /** Batch resolve tracks (Spotify allows up to 50 ids per call). */
    suspend fun getTracks(ids: List<TrackId>): Map<TrackId, Track> {
        if (ids.isEmpty()) return emptyMap()
        val result = LinkedHashMap<TrackId, Track>()
        ids.distinct().forEach { id ->
            getTrack(id)?.let { result[id] = it }
        }
        return result
    }

    suspend fun getCandidates(context: RecommendationContext): List<Track>

    /** Richer pool with related-artist ids for graph scoring. Default wraps [getCandidates]. */
    suspend fun getCandidatePool(context: RecommendationContext): CandidatePool =
        CandidatePool(tracks = getCandidates(context))

    suspend fun playTrack(id: TrackId)

    /** Last successful playback path after [playTrack]. */
    fun playbackMode(): PlaybackMode = PlaybackMode.None

    suspend fun pausePlayback() = Unit
    suspend fun resumePlayback() = Unit

    fun playbackEvents(): Flow<PlaybackEvent> = emptyFlow()

    /**
     * Re-read the external player (e.g. after returning to foreground) and emit
     * the current track if the session UI may be stale.
     */
    suspend fun reconcilePlayback() = Unit
}
