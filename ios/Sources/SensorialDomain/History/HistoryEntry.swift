import Foundation

/// One node on the single linear playback timeline.
public struct HistoryEntry: Hashable, Sendable, Codable {
    public let track: Track
    public let discoveryMode: DiscoveryMode
    public let recommendedAtEpochMs: Int64
    public let listeningProgress: Double?

    public init(
        track: Track,
        discoveryMode: DiscoveryMode = .normal,
        recommendedAtEpochMs: Int64 = 0,
        listeningProgress: Double? = nil
    ) {
        self.track = track
        self.discoveryMode = discoveryMode
        self.recommendedAtEpochMs = recommendedAtEpochMs
        self.listeningProgress = listeningProgress
    }
}
