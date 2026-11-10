package com.sensorial.domain.recommendation

import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.Track
import kotlin.math.abs

/**
 * Numeric offline metrics for recommendation regression / A-B fixtures.
 */
data class OfflineEvalReport(
    val modeDivergence: Boolean,
    val deepenTopId: String?,
    val exploreTopId: String?,
    val artistDiversityAt5: Int,
    val remasterBlocked: Boolean,
    val avgListeningProxy: Double,
    val coldStartTopId: String?,
    val warmTopId: String?,
    val deepenSameArtistStreakRate: Double,
)

object OfflineEvalMetrics {
    /**
     * Proxy for expected engagement: higher when features match preferred + not a novelty shock
     * under Deepen; under Explore rewards mid popularity and novelty vs current.
     */
    fun listeningProxy(
        scored: ScoredTrack,
        current: Track?,
        mode: DiscoveryMode,
    ): Double {
        val popularity = scored.track.popularity.coerceIn(0, 100) / 100.0
        val confidence = scored.track.featureConfidence.coerceIn(0.0, 1.0)
        val affinity = current?.let { trackAffinity(scored.track, it) } ?: 0.5
        return when (mode) {
            DiscoveryMode.Deepen -> (0.55 * affinity + 0.25 * popularity + 0.2 * confidence)
            DiscoveryMode.Explore -> (0.45 * (1.0 - affinity) + 0.35 * (1.0 - abs(popularity - 0.4)) + 0.2 * confidence)
            DiscoveryMode.Normal -> (0.4 * affinity + 0.35 * popularity + 0.25 * confidence)
        }.coerceIn(0.0, 1.0)
    }

    fun artistDiversityAtK(ranked: List<ScoredTrack>, k: Int = 5): Int =
        ranked.take(k).map { it.track.primaryArtist.id.value }.toSet().size

    fun deepenSameArtistStreakRate(
        picks: List<Track>,
        seedArtistId: String,
    ): Double {
        if (picks.isEmpty()) return 0.0
        val same = picks.count { it.primaryArtist.id.value == seedArtistId }
        return same.toDouble() / picks.size
    }

    fun averageListeningProxy(
        ranked: List<ScoredTrack>,
        current: Track?,
        mode: DiscoveryMode,
        k: Int = 5,
    ): Double {
        val slice = ranked.take(k)
        if (slice.isEmpty()) return 0.0
        return slice.map { listeningProxy(it, current, mode) }.average()
    }

    private fun trackAffinity(a: Track, b: Track): Double {
        val sharedGenres = a.genres.intersect(b.genres.toSet()).size.toDouble()
        val sharedArtists = a.artists.map { it.id }.intersect(b.artists.map { it.id }.toSet()).size.toDouble()
        val genrePart = sharedGenres / maxOf(1, a.genres.size)
        val artistPart = if (sharedArtists > 0) 1.0 else 0.0
        val f = a.audioFeatures
        val g = b.audioFeatures
        val features = (
            1.0 - abs(f.energy - g.energy) +
                1.0 - abs(f.danceability - g.danceability) +
                1.0 - abs(f.valence - g.valence) +
                1.0 - (abs(f.tempo - g.tempo) / 80.0).coerceIn(0.0, 1.0)
            ) / 4.0
        return (0.45 * genrePart + 0.25 * artistPart + 0.3 * features).coerceIn(0.0, 1.0)
    }
}
