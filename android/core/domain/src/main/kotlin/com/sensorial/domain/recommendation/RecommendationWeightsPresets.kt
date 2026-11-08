package com.sensorial.domain.recommendation

import com.sensorial.domain.model.DiscoveryMode

object RecommendationWeightsPresets {
    val Balanced = RecommendationWeights()

    val Focused = RecommendationWeights(
        genre = 1.1,
        artist = 1.0,
        features = 0.75,
        similarity = 0.85,
        exploration = 0.35,
        listeningBehavior = 0.65,
        // Keep climate focus, but do not let one artist dominate Deepen forever.
        artistDiversity = 0.85,
        localSimilarity = 0.55,
        artistGraph = 0.7,
        popularityFit = 0.25,
    )

    val Discovery = RecommendationWeights(
        genre = 0.85,
        artist = 0.55,
        features = 0.5,
        similarity = 0.25,
        exploration = 1.15,
        listeningBehavior = 0.45,
        artistDiversity = 0.95,
        localSimilarity = 0.25,
        artistGraph = 0.35,
        popularityFit = 0.55,
        trackRepetition = 1.0,
    )

    fun forMode(mode: DiscoveryMode): RecommendationWeights = when (mode) {
        DiscoveryMode.Deepen -> Focused
        DiscoveryMode.Explore -> Discovery
        DiscoveryMode.Normal -> Balanced
    }
}
