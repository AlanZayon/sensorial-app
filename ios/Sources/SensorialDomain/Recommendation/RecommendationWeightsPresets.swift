import Foundation

public enum RecommendationWeightsPresets {
    public static let balanced = RecommendationWeights()

    public static let focused = RecommendationWeights(
        genre: 1.1,
        artist: 1.0,
        features: 0.75,
        recentSignals: 0.5,
        exploration: 0.35,
        listening: 0,
        listeningBehavior: 0.65,
        contextualTime: 0.35,
        similarity: 0.85,
        artistDiversity: 0.85,
        trackRepetition: 0.85,
        artistFocusRelief: 1.0,
        negativePenalty: 1.0,
        coldStart: 0.08,
        sessionAffinity: 0.45,
        artistGraph: 0.7,
        popularityFit: 0.25,
        eraFit: 0.25,
        localSimilarity: 0.55,
        transitionFit: 0.65,
        deepenRelated: 0.7,
        deepenSameArtist: 0.85
    )

    public static let discovery = RecommendationWeights(
        genre: 0.85,
        artist: 0.55,
        features: 0.5,
        recentSignals: 0.5,
        exploration: 1.15,
        listening: 0,
        listeningBehavior: 0.45,
        contextualTime: 0.35,
        similarity: 0.25,
        artistDiversity: 0.95,
        trackRepetition: 1.0,
        artistFocusRelief: 1.0,
        negativePenalty: 1.0,
        coldStart: 0.08,
        sessionAffinity: 0.45,
        artistGraph: 0.35,
        popularityFit: 0.55,
        eraFit: 0.25,
        localSimilarity: 0.25,
        transitionFit: 0.25,
        deepenRelated: 0.2,
        deepenSameArtist: 0.4
    )

    public static func forMode(_ mode: DiscoveryMode) -> RecommendationWeights {
        switch mode {
        case .deepen: return focused
        case .explore: return discovery
        case .normal: return balanced
        }
    }
}
