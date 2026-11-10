package com.sensorial.domain.recommendation

import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackInteraction

/**
 * Short-lived session intent: decays and never replaces the durable profile.
 */
data class SessionAffinity(
    val genreBoosts: Map<GenreId, Double> = emptyMap(),
    val artistBoosts: Map<ArtistId, Double> = emptyMap(),
    val deepenStreak: Int = 0,
    val exploreStreak: Int = 0,
    val quickSkipStreak: Int = 0,
    /** Temporary exploration nudge from consecutive quick skips (0..1). */
    val explorationBoost: Double = 0.0,
) {
    fun genreBoost(id: GenreId): Double = genreBoosts[id] ?: 0.0

    fun artistBoost(id: ArtistId): Double = artistBoosts[id] ?: 0.0

    fun applying(
        track: Track,
        interaction: TrackInteraction,
        config: SessionAffinityConfig = SessionAffinityConfig(),
    ): SessionAffinity {
        // Keep negative (skip) aversions around longer than temporary positive boosts.
        val decayedGenres = decayMixed(genreBoosts, config.decay, config.skipAversionDecay)
        val decayedArtists = decayMixed(artistBoosts, config.decay, config.skipAversionDecay)
        val isQuickSkip = com.sensorial.domain.preference.QuickSkip.isQuickSkip(
            interaction = interaction,
            trackDurationMs = track.durationMs,
            config = com.sensorial.domain.preference.PreferenceUpdateConfig(
                quickSkipProgress = config.quickSkipProgress,
                quickSkipMinMs = config.quickSkipMinMs,
            ),
        )

        var deepen = deepenStreak
        var explore = exploreStreak
        var skips = if (isQuickSkip) quickSkipStreak + 1 else 0
        when (interaction.discoveryMode) {
            DiscoveryMode.Deepen -> {
                deepen += 1
                explore = 0
            }
            DiscoveryMode.Explore -> {
                explore += 1
                deepen = 0
            }
            DiscoveryMode.Normal -> {
                deepen = 0
                explore = 0
            }
        }

        val listenScale = when {
            interaction.interactionType == InteractionType.NavigationReturn -> 0.7
            isQuickSkip -> config.quickSkipListenScale
            else -> interaction.effectiveListeningProgress * 0.55
        }
        val genres = bump(decayedGenres, track.genres, listenScale, config.clamp)
        val artists = bump(decayedArtists, track.artists.map { it.id }, listenScale * 0.85, config.clamp)
        val exploreBoost = when {
            skips >= config.quickSkipTrigger -> (explorationBoost + config.skipExplorationStep).coerceIn(0.0, 1.0)
            explore > 0 -> (explorationBoost + config.modeExplorationStep * explore).coerceIn(0.0, 1.0)
            deepen > 0 -> (explorationBoost - config.modeExplorationStep * deepen * 0.5).coerceIn(0.0, 1.0)
            else -> (explorationBoost * config.decay).coerceIn(0.0, 1.0)
        }

        return copy(
            genreBoosts = genres,
            artistBoosts = artists,
            deepenStreak = deepen,
            exploreStreak = explore,
            quickSkipStreak = skips,
            explorationBoost = exploreBoost,
        )
    }

    private fun <K> decayMixed(
        map: Map<K, Double>,
        positiveFactor: Double,
        negativeFactor: Double,
    ): Map<K, Double> =
        map.mapValues { (_, v) ->
            v * if (v < 0.0) negativeFactor else positiveFactor
        }.filterValues { kotlin.math.abs(it) >= 0.02 }

    private fun <K> bump(
        current: Map<K, Double>,
        keys: List<K>,
        delta: Double,
        clamp: ClosedFloatingPointRange<Double>,
    ): Map<K, Double> {
        if (keys.isEmpty() || delta == 0.0) return current
        val mutable = current.toMutableMap()
        keys.forEach { key ->
            mutable[key] = ((mutable[key] ?: 0.0) + delta).coerceIn(clamp)
        }
        return mutable
    }

    companion object {
        val EMPTY = SessionAffinity()
    }
}

data class SessionAffinityConfig(
    val decay: Double = 0.88,
    /** Skip aversions decay slower than positive session boosts. */
    val skipAversionDecay: Double = 0.94,
    val quickSkipProgress: Double = 0.12,
    val quickSkipMinMs: Long = 8_000L,
    val quickSkipTrigger: Int = 2,
    val skipExplorationStep: Double = 0.18,
    val modeExplorationStep: Double = 0.08,
    /** Session aversion strength when the user quick-skips a track. */
    val quickSkipListenScale: Double = -0.4,
    val clamp: ClosedFloatingPointRange<Double> = -0.6..0.6,
)
