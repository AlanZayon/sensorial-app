import Foundation

public struct HistoryConfig: Sendable, Equatable {
    public static let defaultMaxSize = 50
    public let maxSize: Int

    public init(maxSize: Int = HistoryConfig.defaultMaxSize) {
        precondition(maxSize >= 1)
        self.maxSize = maxSize
    }
}

public final class PlaybackHistory: @unchecked Sendable {
    private let config: HistoryConfig
    private var entries: [HistoryEntry] = []
    private var index: Int = -1

    public init(config: HistoryConfig = HistoryConfig()) {
        self.config = config
    }

    public var currentTrack: Track? {
        guard index >= 0, index < entries.count else { return nil }
        return entries[index].track
    }

    public var currentEntry: HistoryEntry? {
        guard index >= 0, index < entries.count else { return nil }
        return entries[index]
    }

    public var canGoBack: Bool { index > 0 }
    public var canGoForward: Bool { index >= 0 && index < entries.count - 1 }
    public var size: Int { entries.count }

    public func snapshot() -> [Track] { entries.map(\.track) }
    public func entrySnapshot() -> [HistoryEntry] { entries }

    public func currentIndex() -> Int { index }

    public func push(
        _ track: Track,
        discoveryMode: DiscoveryMode = .normal,
        recommendedAtEpochMs: Int64 = 0
    ) {
        push(
            HistoryEntry(
                track: track,
                discoveryMode: discoveryMode,
                recommendedAtEpochMs: recommendedAtEpochMs
            )
        )
    }

    public func push(_ entry: HistoryEntry) {
        if index < entries.count - 1 {
            entries.removeSubrange((index + 1)..<entries.count)
        }
        entries.append(entry)
        index = entries.count - 1
        trimToMaxSize()
    }

    @discardableResult
    public func previous() -> Track {
        precondition(canGoBack)
        index -= 1
        return entries[index].track
    }

    @discardableResult
    public func next() -> Track {
        precondition(canGoForward)
        index += 1
        return entries[index].track
    }

    public func updateCurrentListeningProgress(_ progress: Double) {
        guard index >= 0, index < entries.count else { return }
        let current = entries[index]
        entries[index] = HistoryEntry(
            track: current.track,
            discoveryMode: current.discoveryMode,
            recommendedAtEpochMs: current.recommendedAtEpochMs,
            listeningProgress: min(1, max(0, progress))
        )
    }

    private func trimToMaxSize() {
        while entries.count > config.maxSize {
            entries.removeFirst()
            index -= 1
        }
        if index < 0 && !entries.isEmpty {
            index = 0
        }
    }
}
