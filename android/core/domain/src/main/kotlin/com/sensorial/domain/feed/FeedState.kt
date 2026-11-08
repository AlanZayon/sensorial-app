package com.sensorial.domain.feed

import com.sensorial.domain.model.Track

enum class PlaybackStatus {
    Loading,
    Playing,
    Paused,
    ChangingTrack,
    Error,
    NoRecommendations,
}

/** How audio is currently delivered (Spotify App Remote vs 30s preview). */
enum class PlaybackMode {
    None,
    Preview,
    SpotifyRemote,
}

data class FeedState(
    val status: PlaybackStatus = PlaybackStatus.Loading,
    val currentTrack: Track? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val upcomingTracks: List<Track> = emptyList(),
    val lastIntentLabel: String? = null,
    val errorMessage: String? = null,
    val playbackMode: PlaybackMode = PlaybackMode.None,
    /** True when the current track was explicitly liked (persists across revisits). */
    val isCurrentTrackLiked: Boolean = false,
    /** True while advancing through a liked-library queue instead of discovery. */
    val libraryPlayback: Boolean = false,
)
