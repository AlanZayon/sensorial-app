import Foundation

public enum MmrSelector {
    public static func lambda(for mode: DiscoveryMode) -> Double {
        switch mode {
        case .deepen: return 0.85
        case .explore: return 0.45
        case .normal: return 0.65
        }
    }

    public static func selectSlate(
        ranked: [ScoredTrack],
        seedSelected: [Track],
        mode: DiscoveryMode,
        trackSimilarity: TrackSimilarity = LocalFeatureTrackSimilarity(),
        lambda: Double? = nil,
        limit: Int? = nil
    ) -> [ScoredTrack] {
        guard !ranked.isEmpty else { return [] }
        let λ = lambda ?? self.lambda(for: mode)
        let cap = limit ?? ranked.count
        var remaining = ranked
        var selectedTracks = seedSelected
        var result: [ScoredTrack] = []

        while !remaining.isEmpty && result.count < cap {
            var bestIdx = 0
            var bestScore = -Double.greatestFiniteMagnitude
            var bestDiversity = 0.0
            for (i, candidate) in remaining.enumerated() {
                let relevance = candidate.score
                let maxSim: Double
                if selectedTracks.isEmpty {
                    maxSim = 0
                } else {
                    maxSim = selectedTracks.map {
                        trackSimilarity.similarity(candidate.track, $0, genreVocabulary: [])
                    }.max() ?? 0
                }
                let mmr = λ * relevance - (1 - λ) * maxSim
                if mmr > bestScore {
                    bestScore = mmr
                    bestIdx = i
                    bestDiversity = -maxSim
                }
            }
            let picked = remaining.remove(at: bestIdx)
            var breakdown = picked.breakdown
            breakdown = ScoreBreakdown(
                genre: breakdown.genre,
                artist: breakdown.artist,
                features: breakdown.features,
                recent: breakdown.recent,
                exploration: breakdown.exploration,
                listening: breakdown.listening,
                listeningBehavior: breakdown.listeningBehavior,
                contextualTime: breakdown.contextualTime,
                similarity: breakdown.similarity,
                session: breakdown.session,
                artistGraph: breakdown.artistGraph,
                popularityFit: breakdown.popularityFit,
                eraFit: breakdown.eraFit,
                localSimilarity: breakdown.localSimilarity,
                transitionFit: breakdown.transitionFit,
                artistDiversity: breakdown.artistDiversity,
                trackRepetition: breakdown.trackRepetition,
                negative: breakdown.negative,
                coldStart: breakdown.coldStart,
                deepenRelated: breakdown.deepenRelated,
                deepenSameArtist: breakdown.deepenSameArtist,
                diversity: bestDiversity * (1 - λ),
                total: bestScore
            )
            result.append(ScoredTrack(track: picked.track, score: bestScore, breakdown: breakdown))
            selectedTracks.append(picked.track)
        }
        return result
    }

    public static func selectNext(
        ranked: [ScoredTrack],
        seedSelected: [Track],
        mode: DiscoveryMode,
        trackSimilarity: TrackSimilarity = LocalFeatureTrackSimilarity()
    ) -> ScoredTrack? {
        selectSlate(
            ranked: ranked,
            seedSelected: seedSelected,
            mode: mode,
            trackSimilarity: trackSimilarity,
            limit: 1
        ).first
    }
}
