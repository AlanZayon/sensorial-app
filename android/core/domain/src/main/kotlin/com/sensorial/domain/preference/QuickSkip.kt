package com.sensorial.domain.preference

import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.TrackInteraction
import kotlin.math.max

/**
 * Duration-aware quick-skip detection shared by profile learning, session affinity, and scoring.
 */
object QuickSkip {
    fun progressThreshold(
        trackDurationMs: Long,
        baseProgress: Double = 0.12,
        minListeningMs: Long = 8_000L,
        maxThreshold: Double = 0.4,
    ): Double {
        if (trackDurationMs <= 0L) return baseProgress
        val fromAbsolute = minListeningMs.toDouble() / trackDurationMs.toDouble()
        return max(baseProgress, fromAbsolute.coerceAtMost(maxThreshold))
    }

    fun isQuickSkip(
        progress: Double,
        trackDurationMs: Long,
        baseProgress: Double = 0.12,
        minListeningMs: Long = 8_000L,
    ): Boolean = progress <= progressThreshold(trackDurationMs, baseProgress, minListeningMs)

    fun isQuickSkip(
        interaction: TrackInteraction,
        trackDurationMs: Long,
        config: PreferenceUpdateConfig = PreferenceUpdateConfig(),
    ): Boolean {
        if (interaction.interactionType == InteractionType.NavigationReturn) return false
        if (interaction.interactionType !in setOf(
                InteractionType.NavigationAdvance,
                InteractionType.Discovery,
            )
        ) {
            return false
        }
        val duration = interaction.trackDurationMs?.takeIf { it > 0 } ?: trackDurationMs
        return isQuickSkip(
            progress = interaction.effectiveListeningProgress,
            trackDurationMs = duration,
            baseProgress = config.quickSkipProgress,
            minListeningMs = config.quickSkipMinMs,
        )
    }
}
