import Foundation

public enum PlaybackStatus: Sendable, Equatable {
    case loading
    case playing
    case paused
    case changingTrack
    case error
    case noRecommendations
}

/// How audio is currently delivered (Spotify App Remote vs 30s preview).
public enum PlaybackMode: Sendable, Equatable {
    case none
    case preview
    case spotifyRemote
}

public struct FeedState: Sendable, Equatable {
    public var status: PlaybackStatus
    public var currentTrack: Track?
    public var canGoBack: Bool
    public var canGoForward: Bool
    public var upcomingTracks: [Track]
    public var lastIntentLabel: String?
    public var errorMessage: String?
    public var playbackMode: PlaybackMode
    public var isCurrentTrackLiked: Bool

    public init(
        status: PlaybackStatus = .loading,
        currentTrack: Track? = nil,
        canGoBack: Bool = false,
        canGoForward: Bool = false,
        upcomingTracks: [Track] = [],
        lastIntentLabel: String? = nil,
        errorMessage: String? = nil,
        playbackMode: PlaybackMode = .none,
        isCurrentTrackLiked: Bool = false
    ) {
        self.status = status
        self.currentTrack = currentTrack
        self.canGoBack = canGoBack
        self.canGoForward = canGoForward
        self.upcomingTracks = upcomingTracks
        self.lastIntentLabel = lastIntentLabel
        self.errorMessage = errorMessage
        self.playbackMode = playbackMode
        self.isCurrentTrackLiked = isCurrentTrackLiked
    }
}
