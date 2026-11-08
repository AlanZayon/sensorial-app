package com.sensorial.domain.gesture

import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.GestureIntensity
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Physical gesture metrics already normalized by the UI layer.
 * Coordinates are relative: origin at gesture start, Y grows upward.
 */
data class RawGestureInput(
    val deltaX: Double,
    val deltaY: Double,
    /** Pixels per second (or density-independent equivalent). */
    val velocity: Double,
    /** 0.0 = left edge, 1.0 = right edge of the interactive surface. */
    val startXNormalized: Double,
    val screenWidth: Double = 1.0,
    val screenHeight: Double = 1.0,
) {
    init {
        require(startXNormalized in 0.0..1.0) { "startXNormalized must be in 0..1" }
        require(screenWidth > 0 && screenHeight > 0) { "screen dimensions must be positive" }
    }
}

sealed interface UserIntent {
    /** Swipe down — previous track in the single timeline. */
    data object NavigateBack : UserIntent

    /** Swipe up — next in history or Normal recommendation at the tip. */
    data object NavigateForward : UserIntent

    /** Horizontal discovery strategy. */
    data class Discover(val mode: DiscoveryMode) : UserIntent

    data object Invalid : UserIntent
}

data class GestureInterpreterConfig(
    /** Minimum release distance (px) before a swipe can commit. */
    val minDistance: Double = 140.0,
    /** Edge navigation disabled by default — vertical axis owns timeline nav. */
    val edgeBackWidthNormalized: Double = 0.0,
    val edgeForwardWidthNormalized: Double = 0.0,
    /** Higher = clearer axis required; reduces accidental diagonal flips. */
    val axisDominanceRatio: Double = 1.35,
    val velocityLowThreshold: Double = 400.0,
    val velocityHighThreshold: Double = 1200.0,
    val distanceLowThreshold: Double = 160.0,
    val distanceHighThreshold: Double = 360.0,
)

/**
 * Maps physical swipe metrics to domain [UserIntent].
 *
 * ↑ next · ↓ previous · ← deepen · → explore
 */
class GestureInterpreter(
    private val config: GestureInterpreterConfig = GestureInterpreterConfig(),
) {
    fun interpret(input: RawGestureInput): UserIntent {
        val distance = hypot(input.deltaX, input.deltaY)
        if (distance < config.minDistance) {
            return UserIntent.Invalid
        }

        val absX = abs(input.deltaX)
        val absY = abs(input.deltaY)

        return when {
            absY >= absX * config.axisDominanceRatio -> {
                if (input.deltaY > 0) UserIntent.NavigateForward else UserIntent.NavigateBack
            }
            absX >= absY * config.axisDominanceRatio -> {
                // ← Deepen, → Explore
                if (input.deltaX < 0) {
                    UserIntent.Discover(DiscoveryMode.Deepen)
                } else {
                    UserIntent.Discover(DiscoveryMode.Explore)
                }
            }
            else -> UserIntent.Invalid
        }
    }

    fun intensityFor(input: RawGestureInput): GestureIntensity {
        val distance = hypot(input.deltaX, input.deltaY)
        return intensityFor(distance, input.velocity)
    }

    fun semanticVelocity(input: RawGestureInput): Double = semanticVelocity(input.velocity)

    private fun intensityFor(distance: Double, velocity: Double): GestureIntensity {
        val distanceScore = when {
            distance >= config.distanceHighThreshold -> 2
            distance >= config.distanceLowThreshold -> 1
            else -> 0
        }
        val velocityScore = when {
            velocity >= config.velocityHighThreshold -> 2
            velocity >= config.velocityLowThreshold -> 1
            else -> 0
        }
        return when (distanceScore + velocityScore) {
            in 0..1 -> GestureIntensity.Low
            in 2..3 -> GestureIntensity.Medium
            else -> GestureIntensity.High
        }
    }

    private fun semanticVelocity(velocity: Double): Double {
        val capped = min(velocity, config.velocityHighThreshold)
        return (capped / config.velocityHighThreshold).coerceIn(0.0, 1.0)
    }
}
