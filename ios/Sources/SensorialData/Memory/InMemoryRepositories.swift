import Foundation
import SensorialDomain
import SwiftData

public actor InMemoryPlaybackHistoryRepository: PlaybackHistoryRepository {
    private var history: PlaybackHistory
    private var continuation: AsyncStream<PlaybackHistorySnapshot>.Continuation?

    public var historyStream: AsyncStream<PlaybackHistorySnapshot> {
        AsyncStream { continuation in
            self.continuation = continuation
            continuation.yield(self.history.toSnapshot())
        }
    }

    public init(maxSize: Int = HistoryConfig.defaultMaxSize) {
        history = PlaybackHistory(config: HistoryConfig(maxSize: maxSize))
    }

    public func push(
        _ track: Track,
        discoveryMode: DiscoveryMode = .normal,
        recommendedAtEpochMs: Int64 = 0
    ) {
        history.push(track, discoveryMode: discoveryMode, recommendedAtEpochMs: recommendedAtEpochMs)
        continuation?.yield(history.toSnapshot())
    }

    public func previous() throws -> Track {
        let track = history.previous()
        continuation?.yield(history.toSnapshot())
        return track
    }

    public func next() throws -> Track {
        let track = history.next()
        continuation?.yield(history.toSnapshot())
        return track
    }

    public func current() -> Track? { history.currentTrack }
    public func canGoBack() -> Bool { history.canGoBack }
    public func canGoForward() -> Bool { history.canGoForward }

    public func reset(seed: Track?) {
        let maxSize = HistoryConfig.defaultMaxSize
        history = PlaybackHistory(config: HistoryConfig(maxSize: maxSize))
        if let seed {
            history.push(seed)
        }
        continuation?.yield(history.toSnapshot())
    }

    public func snapshot() -> PlaybackHistorySnapshot { history.toSnapshot() }
}

public actor InMemoryInteractionRepository: InteractionRepository {
    private var items: [TrackInteraction] = []

    public init() {}

    public func append(_ interaction: TrackInteraction) {
        items.append(interaction)
    }

    public func recent(limit: Int) -> [TrackInteraction] {
        Array(items.suffix(limit).reversed())
    }

    public func clear() {
        items.removeAll()
    }
}

public actor InMemoryUserPreferenceRepository: UserPreferenceRepository {
    private var profile = UserPreferenceProfile()

    public init() {}

    public func get() -> UserPreferenceProfile { profile }

    public func save(_ profile: UserPreferenceProfile) {
        self.profile = profile
    }

    public func clear() {
        profile = UserPreferenceProfile()
    }
}

public actor InMemoryAppSettingsRepository: AppSettingsRepository {
    private var settings = AppSettings()

    public init() {}

    public func get() -> AppSettings { settings }

    public func save(_ settings: AppSettings) {
        self.settings = settings
    }
}

/// App composition root using SwiftData persistence.
public final class SensorialContainer: @unchecked Sendable {
    public let musicProvider: any MusicProvider
    public let historyRepository: SwiftDataPlaybackHistoryRepository
    public let interactionRepository: SwiftDataInteractionRepository
    public let preferenceRepository: SwiftDataUserPreferenceRepository
    public let settingsRepository: SwiftDataAppSettingsRepository
    public let recommendationEngine: ScoredHeuristicRecommendationEngine
    public let modelContainer: ModelContainer
    public let spotifyAuth: SpotifyAuthRepository?
    public let usesSpotify: Bool

    public init(
        modelContainer: ModelContainer,
        forceMock: Bool = SpotifySecrets.useMock(),
        spotifyClientId: String = SpotifySecrets.clientId()
    ) async throws {
        self.modelContainer = modelContainer
        settingsRepository = SwiftDataAppSettingsRepository(container: modelContainer)
        try await settingsRepository.hydrate()
        let settings = await settingsRepository.get()

        preferenceRepository = SwiftDataUserPreferenceRepository(container: modelContainer)
        try await preferenceRepository.hydrate()

        interactionRepository = SwiftDataInteractionRepository(container: modelContainer)

        historyRepository = SwiftDataPlaybackHistoryRepository(
            container: modelContainer,
            maxSize: settings.historyConfig.maxSize
        )
        try await historyRepository.hydrate()

        recommendationEngine = ScoredHeuristicRecommendationEngine(weights: settings.recommendationWeights)

        let config = SpotifyConfig(clientId: spotifyClientId)
        if !forceMock && config.isConfigured {
            let auth = await MainActor.run { SpotifyAuthRepository(config: config) }
            let api = SpotifyWebApi(auth: auth)
            let playback = await MainActor.run {
                HybridPlaybackController(
                    clientId: config.clientId,
                    redirectURI: config.redirectURI.absoluteString
                )
            }
            musicProvider = SpotifyMusicProvider(
                api: api,
                candidates: CandidateQueryBuilder(api: api),
                playback: playback
            )
            spotifyAuth = auth
            usesSpotify = true
        } else {
            musicProvider = MockMusicProvider()
            spotifyAuth = nil
            usesSpotify = false
        }
    }

    public static func create(inMemory: Bool = false) async throws -> SensorialContainer {
        let container = try SensorialModelContainerFactory.make(inMemory: inMemory)
        return try await SensorialContainer(modelContainer: container)
    }

    public func makeSession() -> DiscoverySession {
        DiscoverySession(
            musicProvider: musicProvider,
            historyRepository: historyRepository,
            interactionRepository: interactionRepository,
            preferenceRepository: preferenceRepository,
            recommendationEngine: recommendationEngine
        )
    }
}
