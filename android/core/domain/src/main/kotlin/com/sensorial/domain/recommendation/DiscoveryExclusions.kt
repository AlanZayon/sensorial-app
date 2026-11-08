package com.sensorial.domain.recommendation

import com.sensorial.domain.history.HistoryEntry
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction
import com.sensorial.domain.preference.LikedTrackRef

data class DiscoveryExclusionConfig(
    /** Progress at/above this counts as "already heard enough" for hard exclusion. */
    val heardProgress: Double = 0.35,
)

/**
 * Discovery should surface new music: liked tracks and substantially heard tracks stay out.
 */
object DiscoveryExclusions {
    fun ids(
        likedIds: Set<TrackId>,
        interactions: List<TrackInteraction>,
        historyEntries: List<HistoryEntry>,
        extra: Set<TrackId> = emptySet(),
        config: DiscoveryExclusionConfig = DiscoveryExclusionConfig(),
    ): Set<TrackId> {
        val excluded = LinkedHashSet<TrackId>()
        excluded += likedIds
        excluded += extra
        interactions.forEach { interaction ->
            if (interaction.effectiveListeningProgress >= config.heardProgress) {
                excluded += interaction.trackId
            }
        }
        historyEntries.forEach { entry ->
            excluded += entry.track.id
            val progress = entry.listeningProgress
            if (progress != null && progress >= config.heardProgress) {
                excluded += entry.track.id
            }
        }
        return excluded
    }

    /**
     * Fingerprints of known liked / well-heard works (blocks remasters/re-uploads).
     */
    fun fingerprints(
        knownTracks: Collection<Track>,
        likedIds: Set<TrackId>,
        heardIds: Set<TrackId>,
    ): Set<String> {
        if (knownTracks.isEmpty() || (likedIds.isEmpty() && heardIds.isEmpty())) return emptySet()
        val blockIds = likedIds + heardIds
        return knownTracks
            .asSequence()
            .filter { it.id in blockIds }
            .map { TrackFingerprint.of(it) }
            .toSet()
    }

    fun wellHeardIds(
        interactions: List<TrackInteraction>,
        historyEntries: List<HistoryEntry>,
        config: DiscoveryExclusionConfig = DiscoveryExclusionConfig(),
    ): Set<TrackId> {
        val heard = LinkedHashSet<TrackId>()
        interactions.forEach { interaction ->
            if (interaction.effectiveListeningProgress >= config.heardProgress) {
                heard += interaction.trackId
            }
        }
        historyEntries.forEach { entry ->
            val progress = entry.listeningProgress
            if (progress != null && progress >= config.heardProgress) {
                heard += entry.track.id
            }
        }
        return heard
    }

    fun likedIds(refs: List<LikedTrackRef>): Set<TrackId> =
        refs.mapTo(LinkedHashSet()) { it.trackId }
}
