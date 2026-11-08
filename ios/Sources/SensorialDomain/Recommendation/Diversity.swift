import Foundation

public struct DiversityConfig: Sendable, Equatable {
    public var artistWindow: Int = 8
    public var trackWindow: Int = 20
    public var versionBlockWindow: Int = 16
    public var artistBasePenalty: Double = 0.55
    public var trackBasePenalty: Double = 0.9
    public var versionBasePenalty: Double = 2.4
    public var artistRecencyDecay: Double = 0.85
    public var focusMinSignals: Int = 3
    public var focusLongListenProgress: Double = 0.55
    public var focusSkipProgress: Double = 0.12
    public var focusRelief: Double = 0.75

    public init() {}
}

public enum SameArtistStreak {
    public static func count(
        primaryArtistId: ArtistId?,
        recentTracks: [Track],
        currentTrack: Track? = nil
    ) -> Int {
        guard let primaryArtistId else { return 0 }
        var timeline = recentTracks
        if let currentTrack, !recentTracks.contains(where: { $0.id == currentTrack.id }) {
            timeline.append(currentTrack)
        }
        var streak = 0
        for track in timeline.reversed() {
            let shares = track.artists.contains { $0.id == primaryArtistId }
            if !shares { break }
            streak += 1
        }
        return streak
    }
}

public enum RecentArtistPenalty {
    public static func score(
        candidate: Track,
        recentTracks: [Track],
        config: DiversityConfig = DiversityConfig(),
        focusedArtistId: ArtistId? = nil
    ) -> Double {
        let window = Array(recentTracks.suffix(config.artistWindow))
        guard !window.isEmpty else { return 0 }
        var penalty = 0.0
        for (index, track) in window.enumerated() {
            let shared = candidate.artists.contains { artist in
                track.artists.contains { $0.id == artist.id }
            }
            guard shared else { continue }
            let age = Double(window.count - 1 - index)
            let recency = pow(config.artistRecencyDecay, age)
            penalty += config.artistBasePenalty * recency
        }
        if let primary = candidate.artists.first?.id, primary == focusedArtistId {
            penalty *= max(0, min(1, 1 - config.focusRelief))
        }
        return max(0, penalty)
    }
}

public enum RecentTrackPenalty {
    public static func score(
        candidate: Track,
        recentTracks: [Track],
        config: DiversityConfig = DiversityConfig()
    ) -> Double {
        let window = Array(recentTracks.suffix(config.trackWindow))
        guard !window.isEmpty else { return 0 }

        let idPenalty: Double
        if let index = window.lastIndex(where: { $0.id == candidate.id }) {
            let age = Double(window.count - 1 - index)
            idPenalty = config.trackBasePenalty * exp(-age / 4.0)
        } else {
            idPenalty = 0
        }

        let fingerprint = TrackFingerprint.of(candidate)
        let versionPenalty: Double
        if let index = window.lastIndex(where: {
            $0.id != candidate.id && TrackFingerprint.of($0) == fingerprint
        }) {
            let age = Double(window.count - 1 - index)
            versionPenalty = config.versionBasePenalty * exp(-age / 5.0)
        } else {
            versionPenalty = 0
        }

        return max(idPenalty, versionPenalty)
    }
}

public enum RecentVersionBlock {
    public static func isBlocked(
        candidate: Track,
        recentTracks: [Track],
        currentTrack: Track? = nil,
        config: DiversityConfig = DiversityConfig()
    ) -> Bool {
        let fingerprint = TrackFingerprint.of(candidate)
        if let currentTrack, TrackFingerprint.of(currentTrack) == fingerprint {
            return true
        }
        return recentTracks.suffix(config.versionBlockWindow).contains {
            TrackFingerprint.of($0) == fingerprint
        }
    }

    public static func fingerprints(
        recentTracks: [Track],
        currentTrack: Track? = nil,
        config: DiversityConfig = DiversityConfig()
    ) -> Set<String> {
        var blocked = Set<String>()
        if let currentTrack {
            blocked.insert(TrackFingerprint.of(currentTrack))
        }
        for track in recentTracks.suffix(config.versionBlockWindow) {
            blocked.insert(TrackFingerprint.of(track))
        }
        return blocked
    }
}

public struct ArtistFocusResult: Sendable, Equatable {
    public let focusedArtistId: ArtistId?
    public let score: Double
}

public enum ArtistFocusDetector {
    public static func detect(
        recentInteractions: [TrackInteraction],
        interactionTracks: [TrackId: Track],
        config: DiversityConfig = DiversityConfig()
    ) -> ArtistFocusResult {
        guard !recentInteractions.isEmpty else {
            return ArtistFocusResult(focusedArtistId: nil, score: 0)
        }

        struct Acc {
            var longListens = 0
            var returns = 0
            var likes = 0
            var altSkips = 0
        }

        var byArtist: [ArtistId: Acc] = [:]
        for interaction in recentInteractions.suffix(config.artistWindow * 2) {
            guard let track = interactionTracks[interaction.trackId] else { continue }
            guard let artistId = interaction.artistId ?? track.artists.first?.id else { continue }
            var acc = byArtist[artistId] ?? Acc()
            switch interaction.interactionType {
            case .navigationReturn: acc.returns += 1
            case .explicitLike: acc.likes += 1
            case .explicitUnlike: acc.likes = max(0, acc.likes - 1)
            default: break
            }
            if interaction.effectiveListeningProgress >= config.focusLongListenProgress {
                acc.longListens += 1
            }
            if interaction.effectiveListeningProgress <= config.focusSkipProgress {
                switch interaction.interactionType {
                case .navigationAdvance, .discovery, .preference:
                    acc.altSkips += 1
                default:
                    break
                }
            }
            byArtist[artistId] = acc
        }

        var best: ArtistId?
        var bestScore = 0.0
        for (artistId, acc) in byArtist {
            let signalKinds = [acc.longListens > 0, acc.returns > 0, acc.likes > 0].filter { $0 }.count
            if signalKinds < 2 && (acc.longListens + acc.returns + acc.likes) < config.focusMinSignals {
                continue
            }
            let score = Double(acc.longListens) * 0.35 + Double(acc.returns) * 0.3 + Double(acc.likes) * 0.35
            if score > bestScore {
                bestScore = score
                best = artistId
            }
        }
        return ArtistFocusResult(focusedArtistId: best, score: bestScore)
    }
}
