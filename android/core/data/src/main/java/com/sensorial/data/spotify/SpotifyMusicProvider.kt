package com.sensorial.data.spotify

import android.app.Activity
import com.sensorial.domain.feed.PlaybackMode
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.provider.MusicProvider
import com.sensorial.domain.provider.PlaybackEvent
import com.sensorial.domain.recommendation.CandidatePool
import com.sensorial.domain.recommendation.RecommendationContext
import kotlinx.coroutines.flow.Flow

class SpotifyMusicProvider(
    private val api: SpotifyWebApi,
    private val candidates: CandidateQueryBuilder,
    private val playback: HybridPlaybackController,
) : MusicProvider {

    fun attachHost(activity: Activity) = playback.attachHost(activity)

    fun detachHost() = playback.detachHost()

    fun lastRemoteError(): String? = playback.lastRemoteError

    suspend fun authorizeRemotePlayback(showAuth: Boolean = false): Result<Unit> =
        playback.authorizeRemote(showAuth = showAuth)

    override suspend fun searchTracks(query: String): List<Track> {
        val tracks = api.searchTracks(query)
        playback.rememberAll(tracks)
        return tracks
    }

    override suspend fun getTrack(id: TrackId): Track? {
        val track = api.getTrack(id) ?: return null
        playback.remember(track)
        return track
    }

    override suspend fun getTracks(ids: List<TrackId>): Map<TrackId, Track> {
        if (ids.isEmpty()) return emptyMap()
        val tracks = api.getTracks(ids)
        playback.rememberAll(tracks.values.toList())
        return tracks
    }

    override suspend fun getCandidates(context: RecommendationContext): List<Track> =
        getCandidatePool(context).tracks

    override suspend fun getCandidatePool(context: RecommendationContext): CandidatePool {
        val profile = if (context.prefetch) {
            CandidateQueryBuilder.FetchProfile.Prefetch
        } else {
            CandidateQueryBuilder.FetchProfile.Interactive
        }
        val result = candidates.fetchDetailed(context, profile)
        playback.rememberAll(result.tracks)
        return CandidatePool(
            tracks = result.tracks,
            relatedArtistIds = result.relatedArtistIds,
        )
    }

    override suspend fun playTrack(id: TrackId) {
        playback.play(id.value)
    }

    override fun playbackMode(): PlaybackMode = playback.mode

    override suspend fun pausePlayback() {
        playback.pause()
    }

    override suspend fun resumePlayback() {
        playback.resume()
    }

    override fun playbackEvents(): Flow<PlaybackEvent> = playback.events

    override suspend fun reconcilePlayback() {
        playback.reconcilePlayerState()
    }
}
