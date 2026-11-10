package com.sensorial.domain.recommendation

import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.Track

/**
 * Maximal Marginal Relevance selection over an already-scored candidate list.
 *
 * MMR = λ · relevance − (1−λ) · max sim(c, selected)
 */
object MmrSelector {
    fun lambdaForMode(mode: DiscoveryMode): Double = when (mode) {
        DiscoveryMode.Deepen -> 0.85
        DiscoveryMode.Explore -> 0.45
        DiscoveryMode.Normal -> 0.65
    }

    /**
     * Reorders [ranked] (highest relevance first) into an MMR slate.
     * [seedSelected] are already-played tracks that count toward diversity.
     */
    fun selectSlate(
        ranked: List<ScoredTrack>,
        seedSelected: List<Track>,
        mode: DiscoveryMode,
        trackSimilarity: TrackSimilarity = LocalFeatureTrackSimilarity(),
        lambda: Double = lambdaForMode(mode),
        limit: Int = ranked.size,
    ): List<ScoredTrack> {
        if (ranked.isEmpty()) return emptyList()
        val remaining = ranked.toMutableList()
        val selectedTracks = seedSelected.toMutableList()
        val result = ArrayList<ScoredTrack>(minOf(limit, ranked.size))

        while (remaining.isNotEmpty() && result.size < limit) {
            var bestIdx = 0
            var bestScore = Double.NEGATIVE_INFINITY
            var bestDiversity = 0.0
            for (i in remaining.indices) {
                val candidate = remaining[i]
                val relevance = candidate.score
                val maxSim = if (selectedTracks.isEmpty()) {
                    0.0
                } else {
                    selectedTracks.maxOf { trackSimilarity.similarity(candidate.track, it, emptyList()) }
                }
                val mmr = lambda * relevance - (1.0 - lambda) * maxSim
                if (mmr > bestScore) {
                    bestScore = mmr
                    bestIdx = i
                    bestDiversity = -maxSim
                }
            }
            val picked = remaining.removeAt(bestIdx)
            val withDiversity = picked.copy(
                breakdown = picked.breakdown.copy(
                    diversity = bestDiversity * (1.0 - lambda),
                    total = bestScore,
                ),
                score = bestScore,
            )
            result += withDiversity
            selectedTracks += picked.track
        }
        return result
    }

    fun selectNext(
        ranked: List<ScoredTrack>,
        seedSelected: List<Track>,
        mode: DiscoveryMode,
        trackSimilarity: TrackSimilarity = LocalFeatureTrackSimilarity(),
    ): ScoredTrack? = selectSlate(
        ranked = ranked,
        seedSelected = seedSelected,
        mode = mode,
        trackSimilarity = trackSimilarity,
        limit = 1,
    ).firstOrNull()
}
