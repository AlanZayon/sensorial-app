import Foundation

public struct SessionAffinity: Sendable, Equatable {
    public var genreBoosts: [GenreId: Double]
    public var artistBoosts: [ArtistId: Double]
    public var deepenStreak: Int
    public var exploreStreak: Int
    public var quickSkipStreak: Int
    public var explorationBoost: Double

    public init(
        genreBoosts: [GenreId: Double] = [:],
        artistBoosts: [ArtistId: Double] = [:],
        deepenStreak: Int = 0,
        exploreStreak: Int = 0,
        quickSkipStreak: Int = 0,
        explorationBoost: Double = 0
    ) {
        self.genreBoosts = genreBoosts
        self.artistBoosts = artistBoosts
        self.deepenStreak = deepenStreak
        self.exploreStreak = exploreStreak
        self.quickSkipStreak = quickSkipStreak
        self.explorationBoost = explorationBoost
    }

    public static let empty = SessionAffinity()

    public func genreBoost(_ id: GenreId) -> Double { genreBoosts[id] ?? 0 }
    public func artistBoost(_ id: ArtistId) -> Double { artistBoosts[id] ?? 0 }

    public func applying(track: Track, interaction: TrackInteraction) -> SessionAffinity {
        let positiveDecay = 0.88
        let skipAversionDecay = 0.94
        var genres = genreBoosts.mapValues { $0 * ($0 < 0 ? skipAversionDecay : positiveDecay) }
            .filter { abs($0.value) >= 0.02 }
        var artists = artistBoosts.mapValues { $0 * ($0 < 0 ? skipAversionDecay : positiveDecay) }
            .filter { abs($0.value) >= 0.02 }
        let progress = interaction.effectiveListeningProgress
        let isQuickSkip = QuickSkip.isQuickSkip(
            interaction: interaction,
            trackDurationMs: track.durationMs
        )

        var deepen = deepenStreak
        var explore = exploreStreak
        var skips = isQuickSkip ? quickSkipStreak + 1 : 0
        switch interaction.discoveryMode {
        case .deepen: deepen += 1; explore = 0
        case .explore: explore += 1; deepen = 0
        case .normal: deepen = 0; explore = 0
        }

        let listenScale: Double
        if interaction.interactionType == .navigationReturn {
            listenScale = 0.7
        } else if isQuickSkip {
            listenScale = -0.4
        } else {
            listenScale = progress * 0.55
        }
        for g in track.genres {
            genres[g] = min(0.6, max(-0.6, (genres[g] ?? 0) + listenScale))
        }
        for a in track.artists.map(\.id) {
            artists[a] = min(0.6, max(-0.6, (artists[a] ?? 0) + listenScale * 0.85))
        }

        var boost = explorationBoost
        if skips >= 2 {
            boost = min(1, boost + 0.18)
        } else if explore > 0 {
            boost = min(1, boost + 0.08 * Double(explore))
        } else if deepen > 0 {
            boost = max(0, boost - 0.04 * Double(deepen))
        } else {
            boost *= positiveDecay
        }

        return SessionAffinity(
            genreBoosts: genres,
            artistBoosts: artists,
            deepenStreak: deepen,
            exploreStreak: explore,
            quickSkipStreak: skips,
            explorationBoost: boost
        )
    }
}
