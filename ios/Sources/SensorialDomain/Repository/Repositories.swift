import Foundation

public struct PlaybackHistorySnapshot: Sendable, Equatable {
    public let currentTrack: Track?
    public let canGoBack: Bool
    public let canGoForward: Bool
    public let tracks: [Track]
    public let entries: [HistoryEntry]
    public let currentIndex: Int

    public init(
        currentTrack: Track?,
        canGoBack: Bool,
        canGoForward: Bool,
        tracks: [Track],
        entries: [HistoryEntry] = [],
        currentIndex: Int
    ) {
        self.currentTrack = currentTrack
        self.canGoBack = canGoBack
        self.canGoForward = canGoForward
        self.tracks = tracks
        self.entries = entries.isEmpty ? tracks.map { HistoryEntry(track: $0) } : entries
        self.currentIndex = currentIndex
    }
}

public extension PlaybackHistory {
    func toSnapshot() -> PlaybackHistorySnapshot {
        PlaybackHistorySnapshot(
            currentTrack: currentTrack,
            canGoBack: canGoBack,
            canGoForward: canGoForward,
            tracks: snapshot(),
            entries: entrySnapshot(),
            currentIndex: currentIndex()
        )
    }
}

public protocol PlaybackHistoryRepository: Sendable {
    var historyStream: AsyncStream<PlaybackHistorySnapshot> { get }
    func push(
        _ track: Track,
        discoveryMode: DiscoveryMode,
        recommendedAtEpochMs: Int64
    ) async
    func previous() async throws -> Track
    func next() async throws -> Track
    func current() async -> Track?
    func canGoBack() async -> Bool
    func canGoForward() async -> Bool
    func reset(seed: Track?) async
    func snapshot() async -> PlaybackHistorySnapshot
}

public extension PlaybackHistoryRepository {
    func push(_ track: Track) async {
        await push(track, discoveryMode: .normal, recommendedAtEpochMs: 0)
    }
}

public protocol InteractionRepository: Sendable {
    func append(_ interaction: TrackInteraction) async
    func recent(limit: Int) async -> [TrackInteraction]
    func all() async -> [TrackInteraction]
    func clear() async
}

public extension InteractionRepository {
    func all() async -> [TrackInteraction] {
        await recent(limit: 10_000)
    }
}

public protocol UserPreferenceRepository: Sendable {
    func get() async -> UserPreferenceProfile
    func save(_ profile: UserPreferenceProfile) async
    func clear() async
}

public struct AppSettings: Sendable, Equatable {
    public var historyConfig: HistoryConfig
    public var recommendationWeights: RecommendationWeights
    public var banditState: ContextualWeightPolicyState

    public init(
        historyConfig: HistoryConfig = HistoryConfig(),
        recommendationWeights: RecommendationWeights = RecommendationWeights(),
        banditState: ContextualWeightPolicyState = .empty
    ) {
        self.historyConfig = historyConfig
        self.recommendationWeights = recommendationWeights
        self.banditState = banditState
    }
}

public protocol AppSettingsRepository: Sendable {
    func get() async -> AppSettings
    func save(_ settings: AppSettings) async
}
