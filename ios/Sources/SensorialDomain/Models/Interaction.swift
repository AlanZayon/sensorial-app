import Foundation

public enum InteractionType: String, Sendable, Codable {
    case preference
    case navigationAdvance
    case navigationReturn
    case discovery
    case explicitLike
    case explicitDislike
    case explicitUnlike
}

public enum VerticalIntent: String, Sendable, Codable {
    case positive
    case negative
    case none
}

public enum HorizontalIntent: String, Sendable, Codable {
    case similar
    case explore
    case none
}

public enum GestureIntensity: String, Sendable, Codable {
    case low
    case medium
    case high
}

public struct TrackInteraction: Hashable, Sendable, Codable, Identifiable {
    public let id: InteractionId
    public let trackId: TrackId
    public let interactionType: InteractionType
    public let verticalIntent: VerticalIntent
    public let horizontalIntent: HorizontalIntent
    public let gestureIntensity: GestureIntensity
    public let gestureVelocity: Double
    public let listeningDurationMs: Int64
    public let timestampEpochMs: Int64
    public let artistId: ArtistId?
    public let trackDurationMs: Int64?
    public let listeningProgress: Double
    public let discoveryMode: DiscoveryMode
    public let localHour: Int
    public let dayOfWeek: Int
    public let navigationAction: NavigationAction

    public init(
        id: InteractionId,
        trackId: TrackId,
        interactionType: InteractionType,
        verticalIntent: VerticalIntent = .none,
        horizontalIntent: HorizontalIntent = .none,
        gestureIntensity: GestureIntensity = .medium,
        gestureVelocity: Double = 0.5,
        listeningDurationMs: Int64,
        timestampEpochMs: Int64,
        artistId: ArtistId? = nil,
        trackDurationMs: Int64? = nil,
        listeningProgress: Double = 0,
        discoveryMode: DiscoveryMode = .normal,
        localHour: Int = 12,
        dayOfWeek: Int = 1,
        navigationAction: NavigationAction = .none
    ) {
        precondition((0...1).contains(gestureVelocity))
        precondition(listeningDurationMs >= 0)
        precondition((0...1).contains(listeningProgress))
        precondition((0...23).contains(localHour))
        precondition((1...7).contains(dayOfWeek))
        self.id = id
        self.trackId = trackId
        self.interactionType = interactionType
        self.verticalIntent = verticalIntent
        self.horizontalIntent = horizontalIntent
        self.gestureIntensity = gestureIntensity
        self.gestureVelocity = gestureVelocity
        self.listeningDurationMs = listeningDurationMs
        self.timestampEpochMs = timestampEpochMs
        self.artistId = artistId
        self.trackDurationMs = trackDurationMs
        self.listeningProgress = listeningProgress
        self.discoveryMode = discoveryMode
        self.localHour = localHour
        self.dayOfWeek = dayOfWeek
        self.navigationAction = navigationAction
    }

    public var effectiveListeningProgress: Double {
        if listeningProgress > 0 { return listeningProgress }
        if let duration = trackDurationMs, duration > 0 {
            return min(1, max(0, Double(listeningDurationMs) / Double(duration)))
        }
        return 0
    }
}
