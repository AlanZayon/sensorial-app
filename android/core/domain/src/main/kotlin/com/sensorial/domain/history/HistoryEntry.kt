package com.sensorial.domain.history

import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.Track

/**
 * One node on the single linear playback timeline.
 */
data class HistoryEntry(
    val track: Track,
    val discoveryMode: DiscoveryMode = DiscoveryMode.Normal,
    val recommendedAtEpochMs: Long = 0L,
    val listeningProgress: Double? = null,
)
