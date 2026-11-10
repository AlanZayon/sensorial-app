import Foundation

public struct DiscoveryExclusionConfig: Sendable {
    public var heardProgress: Double

    public init(heardProgress: Double = 0.35) {
        self.heardProgress = heardProgress
    }
}

public enum DiscoveryExclusions {
    public static func ids(
        likedIds: Set<TrackId>,
        interactions: [TrackInteraction],
        historyEntries: [HistoryEntry],
        extra: Set<TrackId> = [],
        config: DiscoveryExclusionConfig = DiscoveryExclusionConfig()
    ) -> Set<TrackId> {
        var excluded = likedIds.union(extra)
        for interaction in interactions where interaction.effectiveListeningProgress >= config.heardProgress {
            excluded.insert(interaction.trackId)
        }
        for entry in historyEntries {
            excluded.insert(entry.track.id)
            if let progress = entry.listeningProgress, progress >= config.heardProgress {
                excluded.insert(entry.track.id)
            }
        }
        return excluded
    }

    public static func fingerprints(
        knownTracks: [Track],
        likedIds: Set<TrackId>,
        heardIds: Set<TrackId>
    ) -> Set<String> {
        guard !knownTracks.isEmpty, !(likedIds.isEmpty && heardIds.isEmpty) else { return [] }
        let blockIds = likedIds.union(heardIds)
        return Set(knownTracks.filter { blockIds.contains($0.id) }.map { TrackFingerprint.of($0) })
    }

    public static func wellHeardIds(
        interactions: [TrackInteraction],
        historyEntries: [HistoryEntry],
        config: DiscoveryExclusionConfig = DiscoveryExclusionConfig()
    ) -> Set<TrackId> {
        var heard = Set<TrackId>()
        for interaction in interactions where interaction.effectiveListeningProgress >= config.heardProgress {
            heard.insert(interaction.trackId)
        }
        for entry in historyEntries {
            if let progress = entry.listeningProgress, progress >= config.heardProgress {
                heard.insert(entry.track.id)
            }
        }
        return heard
    }
}
