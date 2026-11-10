package com.sensorial.domain.recommendation

import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.TrackInteraction
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

enum class WeightArm {
    Balanced,
    Focused,
    Discovery,
}

data class BanditArmStats(
    val pulls: Int = 0,
    val totalReward: Double = 0.0,
) {
    val mean: Double get() = if (pulls == 0) 0.0 else totalReward / pulls

    fun record(reward: Double): BanditArmStats = copy(
        pulls = pulls + 1,
        totalReward = totalReward + reward,
    )
}

data class ContextualWeightPolicyState(
    val arms: Map<WeightArm, BanditArmStats> = WeightArm.entries.associateWith { BanditArmStats() },
    val lastArm: WeightArm? = null,
) {
    companion object {
        val EMPTY = ContextualWeightPolicyState()
    }
}

data class WeightPolicyDecision(
    val arm: WeightArm,
    val weights: RecommendationWeights,
    val explored: Boolean,
)

/**
 * Lightweight on-device ε-greedy / UCB hybrid over weight presets.
 * Falls back to mode presets when cold (few pulls).
 */
class ContextualWeightPolicy(
    private val epsilon: Double = 0.12,
    private val ucbC: Double = 0.35,
    private val minPullsBeforeLearn: Int = 4,
    private val progressWeight: Double = 0.7,
    private val likeWeight: Double = 0.35,
    private val quickSkipWeight: Double = 0.55,
    private val random: Random = Random.Default,
) {
    fun select(
        mode: DiscoveryMode,
        state: ContextualWeightPolicyState,
        isColdStart: Boolean,
        featureConfidenceMean: Double = 1.0,
    ): WeightPolicyDecision {
        val defaultArm = defaultArmForMode(mode)
        if (isColdStart || state.arms.values.sumOf { it.pulls } < minPullsBeforeLearn) {
            return WeightPolicyDecision(
                arm = defaultArm,
                weights = weightsFor(defaultArm),
                explored = false,
            )
        }
        // Prefer mode-aligned arm; occasionally explore or pick UCB winner among compatible arms.
        val candidates = armsForMode(mode)
        val explore = random.nextDouble() < epsilon * (0.5 + 0.5 * (1.0 - featureConfidenceMean.coerceIn(0.0, 1.0)))
        val arm = if (explore) {
            candidates.random(random)
        } else {
            val totalPulls = state.arms.values.sumOf { it.pulls }.coerceAtLeast(1)
            candidates.maxBy { arm ->
                val stats = state.arms[arm] ?: BanditArmStats()
                if (stats.pulls == 0) Double.POSITIVE_INFINITY
                else stats.mean + ucbC * sqrt(ln(totalPulls.toDouble()) / stats.pulls)
            }
        }
        return WeightPolicyDecision(
            arm = arm,
            weights = weightsFor(arm),
            explored = explore || arm != defaultArm,
        )
    }

    fun reward(interaction: TrackInteraction, wasQuickSkip: Boolean): Double {
        val progress = interaction.effectiveListeningProgress.coerceIn(0.0, 1.0)
        val likeBonus = when (interaction.interactionType) {
            InteractionType.ExplicitLike -> 1.0
            InteractionType.ExplicitDislike, InteractionType.ExplicitUnlike -> -0.5
            else -> 0.0
        }
        val skipPenalty = if (wasQuickSkip) 1.0 else 0.0
        return (progressWeight * progress + likeWeight * likeBonus - quickSkipWeight * skipPenalty)
            .coerceIn(-1.0, 1.5)
    }

    fun update(
        state: ContextualWeightPolicyState,
        arm: WeightArm,
        reward: Double,
    ): ContextualWeightPolicyState {
        val current = state.arms[arm] ?: BanditArmStats()
        return state.copy(
            arms = state.arms + (arm to current.record(reward)),
            lastArm = arm,
        )
    }

    fun weightsFor(arm: WeightArm): RecommendationWeights = when (arm) {
        WeightArm.Balanced -> RecommendationWeightsPresets.Balanced
        WeightArm.Focused -> RecommendationWeightsPresets.Focused
        WeightArm.Discovery -> RecommendationWeightsPresets.Discovery
    }

    private fun defaultArmForMode(mode: DiscoveryMode): WeightArm = when (mode) {
        DiscoveryMode.Deepen -> WeightArm.Focused
        DiscoveryMode.Explore -> WeightArm.Discovery
        DiscoveryMode.Normal -> WeightArm.Balanced
    }

    private fun armsForMode(mode: DiscoveryMode): List<WeightArm> = when (mode) {
        DiscoveryMode.Deepen -> listOf(WeightArm.Focused, WeightArm.Balanced)
        DiscoveryMode.Explore -> listOf(WeightArm.Discovery, WeightArm.Balanced)
        DiscoveryMode.Normal -> listOf(WeightArm.Balanced, WeightArm.Focused, WeightArm.Discovery)
    }
}
