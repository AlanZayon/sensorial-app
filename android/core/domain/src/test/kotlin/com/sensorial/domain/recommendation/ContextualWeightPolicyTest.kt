package com.sensorial.domain.recommendation

import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.InteractionId
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextualWeightPolicyTest {
    @Test
    fun `cold start uses mode default arm`() {
        val policy = ContextualWeightPolicy(random = Random(1))
        val deepen = policy.select(DiscoveryMode.Deepen, ContextualWeightPolicyState.EMPTY, isColdStart = true)
        val explore = policy.select(DiscoveryMode.Explore, ContextualWeightPolicyState.EMPTY, isColdStart = true)
        assertEquals(WeightArm.Focused, deepen.arm)
        assertEquals(WeightArm.Discovery, explore.arm)
    }

    @Test
    fun `reward prefers completion over quick skip`() {
        val policy = ContextualWeightPolicy()
        val good = TrackInteraction(
            id = InteractionId("1"),
            trackId = TrackId("t"),
            interactionType = InteractionType.Discovery,
            listeningDurationMs = 90_000,
            listeningProgress = 0.8,
            timestampEpochMs = 1,
        )
        val skip = good.copy(listeningProgress = 0.05, listeningDurationMs = 3_000)
        assertTrue(policy.reward(good, wasQuickSkip = false) > policy.reward(skip, wasQuickSkip = true))
    }

    @Test
    fun `update accumulates arm stats and improves mean after positive rewards`() {
        val policy = ContextualWeightPolicy(random = Random(2), minPullsBeforeLearn = 0, epsilon = 0.0)
        var state = ContextualWeightPolicyState.EMPTY
        repeat(5) {
            state = policy.update(state, WeightArm.Focused, reward = 0.9)
        }
        repeat(5) {
            state = policy.update(state, WeightArm.Balanced, reward = 0.2)
        }
        repeat(5) {
            state = policy.update(state, WeightArm.Discovery, reward = 0.1)
        }
        val decision = policy.select(
            mode = DiscoveryMode.Deepen,
            state = state,
            isColdStart = false,
        )
        assertEquals(WeightArm.Focused, decision.arm)
        assertTrue((state.arms[WeightArm.Focused]?.mean ?: 0.0) > (state.arms[WeightArm.Balanced]?.mean ?: 0.0))
    }
}
