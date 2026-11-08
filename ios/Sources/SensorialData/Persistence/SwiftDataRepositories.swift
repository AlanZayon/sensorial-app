import Foundation
import SensorialDomain
import SwiftData

public enum SensorialModelContainerFactory {
    public static func make(inMemory: Bool = false) throws -> ModelContainer {
        let schema = Schema([
            HistoryMetaModel.self,
            HistoryTrackModel.self,
            InteractionModel.self,
            PreferenceModel.self,
            SettingsModel.self,
        ])
        let configuration = ModelConfiguration(isStoredInMemoryOnly: inMemory)
        return try ModelContainer(for: schema, configurations: [configuration])
    }
}

public actor SwiftDataPlaybackHistoryRepository: PlaybackHistoryRepository {
    private let context: ModelContext
    private var history: PlaybackHistory
    private var maxSize: Int
    private var continuation: AsyncStream<PlaybackHistorySnapshot>.Continuation?

    public var historyStream: AsyncStream<PlaybackHistorySnapshot> {
        AsyncStream { continuation in
            self.continuation = continuation
            continuation.yield(self.history.toSnapshot())
        }
    }

    public init(container: ModelContainer, maxSize: Int = HistoryConfig.defaultMaxSize) {
        self.context = ModelContext(container)
        self.maxSize = maxSize
        self.history = PlaybackHistory(config: HistoryConfig(maxSize: maxSize))
    }

    public func hydrate() throws {
        let metas = try context.fetch(FetchDescriptor<HistoryMetaModel>())
        let tracks = try context.fetch(FetchDescriptor<HistoryTrackModel>(
            sortBy: [SortDescriptor(\.position)]
        ))
        if let meta = metas.first {
            maxSize = meta.maxSize
        }
        history = PlaybackHistory(config: HistoryConfig(maxSize: maxSize))
        for item in tracks {
            history.push(try PersistenceCodec.decodeHistoryEntry(item.trackData))
        }
        let target = metas.first?.currentIndex ?? history.currentIndex()
        while history.canGoBack && history.currentIndex() > target {
            _ = history.previous()
        }
        while history.canGoForward && history.currentIndex() < target {
            _ = history.next()
        }
        continuation?.yield(history.toSnapshot())
    }

    public func push(
        _ track: Track,
        discoveryMode: DiscoveryMode = .normal,
        recommendedAtEpochMs: Int64 = 0
    ) async {
        history.push(track, discoveryMode: discoveryMode, recommendedAtEpochMs: recommendedAtEpochMs)
        try? persist()
        continuation?.yield(history.toSnapshot())
    }

    public func previous() throws -> Track {
        let track = history.previous()
        try? persist()
        continuation?.yield(history.toSnapshot())
        return track
    }

    public func next() throws -> Track {
        let track = history.next()
        try? persist()
        continuation?.yield(history.toSnapshot())
        return track
    }

    public func current() -> Track? { history.currentTrack }
    public func canGoBack() -> Bool { history.canGoBack }
    public func canGoForward() -> Bool { history.canGoForward }
    public func snapshot() -> PlaybackHistorySnapshot { history.toSnapshot() }

    public func reset(seed: Track?) async {
        history = PlaybackHistory(config: HistoryConfig(maxSize: maxSize))
        if let seed { history.push(seed) }
        try? persist()
        continuation?.yield(history.toSnapshot())
    }

    private func persist() throws {
        let existingTracks = try context.fetch(FetchDescriptor<HistoryTrackModel>())
        existingTracks.forEach(context.delete)
        let existingMeta = try context.fetch(FetchDescriptor<HistoryMetaModel>())
        existingMeta.forEach(context.delete)

        let snapshot = history.toSnapshot()
        for (index, entry) in snapshot.entries.enumerated() {
            context.insert(HistoryTrackModel(position: index, trackData: try PersistenceCodec.encodeHistoryEntry(entry)))
        }
        context.insert(
            HistoryMetaModel(
                currentIndex: snapshot.currentIndex,
                maxSize: maxSize
            )
        )
        try context.save()
    }
}

public actor SwiftDataInteractionRepository: InteractionRepository {
    private let context: ModelContext

    public init(container: ModelContainer) {
        self.context = ModelContext(container)
    }

    public func append(_ interaction: TrackInteraction) async {
        let model = InteractionModel(
            id: interaction.id.value,
            trackId: interaction.trackId.value,
            interactionType: interaction.interactionType.rawValue,
            verticalIntent: interaction.verticalIntent.rawValue,
            horizontalIntent: interaction.horizontalIntent.rawValue,
            gestureIntensity: interaction.gestureIntensity.rawValue,
            gestureVelocity: interaction.gestureVelocity,
            listeningDurationMs: interaction.listeningDurationMs,
            timestampEpochMs: interaction.timestampEpochMs
        )
        context.insert(model)
        try? context.save()
    }

    public func recent(limit: Int) async -> [TrackInteraction] {
        let descriptor = FetchDescriptor<InteractionModel>(
            sortBy: [SortDescriptor(\.timestampEpochMs, order: .reverse)]
        )
        let models = (try? context.fetch(descriptor)) ?? []
        return Array(models.prefix(limit)).reversed().compactMap { $0.toDomain() }
    }

    public func clear() async {
        let models = (try? context.fetch(FetchDescriptor<InteractionModel>())) ?? []
        models.forEach(context.delete)
        try? context.save()
    }
}

public actor SwiftDataUserPreferenceRepository: UserPreferenceRepository {
    private let context: ModelContext
    private var cached = UserPreferenceProfile()

    public init(container: ModelContainer) {
        self.context = ModelContext(container)
    }

    public func hydrate() throws {
        if let model = try context.fetch(FetchDescriptor<PreferenceModel>()).first {
            cached = try PersistenceCodec.decodeProfile(model.profileData)
        }
    }

    public func get() -> UserPreferenceProfile { cached }

    public func save(_ profile: UserPreferenceProfile) async {
        cached = profile
        let existing = (try? context.fetch(FetchDescriptor<PreferenceModel>())) ?? []
        existing.forEach(context.delete)
        if let data = try? PersistenceCodec.encodeProfile(profile) {
            context.insert(PreferenceModel(profileData: data))
            try? context.save()
        }
    }

    public func clear() async {
        cached = UserPreferenceProfile()
        let existing = (try? context.fetch(FetchDescriptor<PreferenceModel>())) ?? []
        existing.forEach(context.delete)
        try? context.save()
    }
}

public actor SwiftDataAppSettingsRepository: AppSettingsRepository {
    private let context: ModelContext
    private var cached = AppSettings()

    public init(container: ModelContainer) {
        self.context = ModelContext(container)
    }

    public func hydrate() throws {
        if let model = try context.fetch(FetchDescriptor<SettingsModel>()).first {
            cached = try PersistenceCodec.decodeSettings(model.settingsData)
        }
    }

    public func get() -> AppSettings { cached }

    public func save(_ settings: AppSettings) async {
        cached = settings
        let existing = (try? context.fetch(FetchDescriptor<SettingsModel>())) ?? []
        existing.forEach(context.delete)
        if let data = try? PersistenceCodec.encodeSettings(settings) {
            context.insert(SettingsModel(settingsData: data))
            try? context.save()
        }
    }
}

private extension InteractionModel {
    func toDomain() -> TrackInteraction? {
        guard
            let type = InteractionType(rawValue: interactionType),
            let vertical = VerticalIntent(rawValue: verticalIntent),
            let horizontal = HorizontalIntent(rawValue: horizontalIntent),
            let intensity = GestureIntensity(rawValue: gestureIntensity)
        else { return nil }
        return TrackInteraction(
            id: InteractionId(id),
            trackId: TrackId(trackId),
            interactionType: type,
            verticalIntent: vertical,
            horizontalIntent: horizontal,
            gestureIntensity: intensity,
            gestureVelocity: gestureVelocity,
            listeningDurationMs: listeningDurationMs,
            timestampEpochMs: timestampEpochMs
        )
    }
}
