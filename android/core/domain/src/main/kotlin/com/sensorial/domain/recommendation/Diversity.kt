package com.sensorial.domain.recommendation

import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction
import com.sensorial.domain.model.InteractionType
import kotlin.math.exp

data class DiversityConfig(
    val artistWindow: Int = 8,
    val trackWindow: Int = 20,
    /** Hard-block same work (title+artist) across this many recent plays. */
    val versionBlockWindow: Int = 16,
    val artistBasePenalty: Double = 0.55,
    val trackBasePenalty: Double = 0.9,
    /** Soft penalty for same work outside the hard block window. */
    val versionBasePenalty: Double = 2.4,
    val artistRecencyDecay: Double = 0.85,
    val focusMinSignals: Int = 3,
    val focusLongListenProgress: Double = 0.55,
    val focusSkipProgress: Double = 0.12,
    val focusRelief: Double = 0.75,
)

/**
 * How many trailing plays share [primaryArtistId] (including [currentTrack] if missing from recent).
 */
object SameArtistStreak {
    fun count(
        primaryArtistId: ArtistId?,
        recentTracks: List<Track>,
        currentTrack: Track? = null,
    ): Int {
        if (primaryArtistId == null) return 0
        val timeline = ArrayList<Track>(recentTracks.size + 1)
        timeline.addAll(recentTracks)
        if (currentTrack != null && recentTracks.none { it.id == currentTrack.id }) {
            timeline.add(currentTrack)
        }
        var streak = 0
        for (index in timeline.lastIndex downTo 0) {
            val shares = timeline[index].artists.any { it.id == primaryArtistId }
            if (!shares) break
            streak++
        }
        return streak
    }
}

/**
 * Penalizes artists that appeared often in the recent timeline.
 */
object RecentArtistPenalty {
    fun score(
        candidate: Track,
        recentTracks: List<Track>,
        config: DiversityConfig = DiversityConfig(),
        focusedArtistId: ArtistId? = null,
    ): Double {
        val window = recentTracks.takeLast(config.artistWindow)
        if (window.isEmpty()) return 0.0
        var penalty = 0.0
        window.forEachIndexed { index, track ->
            val shared = candidate.artists.any { artist -> track.artists.any { it.id == artist.id } }
            if (!shared) return@forEachIndexed
            val recency = config.artistRecencyDecay.pow((window.size - 1 - index).toDouble())
            penalty += config.artistBasePenalty * recency
        }
        val primary = candidate.artists.firstOrNull()?.id
        if (primary != null && primary == focusedArtistId) {
            penalty *= (1.0 - config.focusRelief).coerceIn(0.0, 1.0)
        }
        return penalty.coerceAtLeast(0.0)
    }

    private fun Double.pow(exp: Double): Double = Math.pow(this, exp)
}

/**
 * Penalizes recommending a track already heard recently.
 * Matches both exact track ids and alternate versions of the same work.
 */
object RecentTrackPenalty {
    fun score(
        candidate: Track,
        recentTracks: List<Track>,
        config: DiversityConfig = DiversityConfig(),
    ): Double {
        val window = recentTracks.takeLast(config.trackWindow)
        if (window.isEmpty()) return 0.0

        val idIndex = window.indexOfLast { it.id == candidate.id }
        val idPenalty = if (idIndex >= 0) {
            val age = (window.size - 1 - idIndex).toDouble()
            config.trackBasePenalty * exp(-age / 4.0)
        } else {
            0.0
        }

        val fingerprint = TrackFingerprint.of(candidate)
        val versionIndex = window.indexOfLast {
            it.id != candidate.id && TrackFingerprint.of(it) == fingerprint
        }
        val versionPenalty = if (versionIndex >= 0) {
            val age = (window.size - 1 - versionIndex).toDouble()
            config.versionBasePenalty * exp(-age / 5.0)
        } else {
            0.0
        }

        return maxOf(idPenalty, versionPenalty)
    }
}

/**
 * Hard-blocks alternate versions of works heard in the recent timeline.
 * Occasional rematches outside [DiversityConfig.versionBlockWindow] remain possible.
 */
object RecentVersionBlock {
    fun isBlocked(
        candidate: Track,
        recentTracks: List<Track>,
        currentTrack: Track? = null,
        config: DiversityConfig = DiversityConfig(),
    ): Boolean {
        val fingerprint = TrackFingerprint.of(candidate)
        if (currentTrack != null && TrackFingerprint.of(currentTrack) == fingerprint) {
            return true
        }
        val window = recentTracks.takeLast(config.versionBlockWindow)
        return window.any { TrackFingerprint.of(it) == fingerprint }
    }

    fun fingerprints(
        recentTracks: List<Track>,
        currentTrack: Track? = null,
        config: DiversityConfig = DiversityConfig(),
    ): Set<String> {
        val blocked = LinkedHashSet<String>()
        if (currentTrack != null) blocked += TrackFingerprint.of(currentTrack)
        recentTracks.takeLast(config.versionBlockWindow).forEach {
            blocked += TrackFingerprint.of(it)
        }
        return blocked
    }
}

data class ArtistFocusResult(
    val focusedArtistId: ArtistId?,
    val score: Double,
)

/**
 * Detects temporary focus on an artist from multiple behavioral signals.
 * A single quick skip alone never activates focus.
 */
object ArtistFocusDetector {
    fun detect(
        recentInteractions: List<TrackInteraction>,
        interactionTracks: Map<TrackId, Track>,
        config: DiversityConfig = DiversityConfig(),
    ): ArtistFocusResult {
        if (recentInteractions.isEmpty()) return ArtistFocusResult(null, 0.0)

        data class Acc(var longListens: Int = 0, var returns: Int = 0, var likes: Int = 0, var altSkips: Int = 0)

        val byArtist = linkedMapOf<ArtistId, Acc>()
        recentInteractions.takeLast(config.artistWindow * 2).forEach { interaction ->
            val track = interactionTracks[interaction.trackId] ?: return@forEach
            val artistId = interaction.artistId ?: track.primaryArtist.id
            val acc = byArtist.getOrPut(artistId) { Acc() }
            when (interaction.interactionType) {
                InteractionType.NavigationReturn -> acc.returns += 1
                InteractionType.ExplicitLike -> acc.likes += 1
                InteractionType.ExplicitUnlike -> acc.likes = (acc.likes - 1).coerceAtLeast(0)
                else -> Unit
            }
            if (interaction.effectiveListeningProgress >= config.focusLongListenProgress) {
                acc.longListens += 1
            }
            if (interaction.effectiveListeningProgress <= config.focusSkipProgress &&
                interaction.interactionType in setOf(
                    InteractionType.NavigationAdvance,
                    InteractionType.Discovery,
                    InteractionType.Preference,
                )
            ) {
                // Skips of *other* artists counted against each non-focused candidate later.
                acc.altSkips += 1
            }
        }

        var best: ArtistId? = null
        var bestScore = 0.0
        byArtist.forEach { (artistId, acc) ->
            val signalCount = listOf(acc.longListens > 0, acc.returns > 0, acc.likes > 0)
                .count { it }
            // Require multi-signal evidence (not a lone skip).
            if (signalCount < 2 && acc.longListens + acc.returns + acc.likes < config.focusMinSignals) {
                return@forEach
            }
            val score = acc.longListens * 0.35 + acc.returns * 0.3 + acc.likes * 0.35
            if (score > bestScore) {
                bestScore = score
                best = artistId
            }
        }
        return ArtistFocusResult(focusedArtistId = best, score = bestScore)
    }
}
