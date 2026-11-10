import Foundation

public enum WeightArm: String, Sendable, CaseIterable {
    case balanced
    case focused
    case discovery
}

public struct BanditArmStats: Sendable, Equatable {
    public var pulls: Int
    public var totalReward: Double

    public init(pulls: Int = 0, totalReward: Double = 0) {
        self.pulls = pulls
        self.totalReward = totalReward
    }

    public var mean: Double { pulls == 0 ? 0 : totalReward / Double(pulls) }

    public func record(reward: Double) -> BanditArmStats {
        BanditArmStats(pulls: pulls + 1, totalReward: totalReward + reward)
    }
}

public struct ContextualWeightPolicyState: Sendable, Equatable {
    public var arms: [WeightArm: BanditArmStats]
    public var lastArm: WeightArm?

    public init(
        arms: [WeightArm: BanditArmStats] = Dictionary(
            uniqueKeysWithValues: WeightArm.allCases.map { ($0, BanditArmStats()) }
        ),
        lastArm: WeightArm? = nil
    ) {
        self.arms = arms
        self.lastArm = lastArm
    }

    public static let empty = ContextualWeightPolicyState()
}

public struct WeightPolicyDecision: Sendable {
    public let arm: WeightArm
    public let weights: RecommendationWeights
    public let explored: Bool
}

public struct ContextualWeightPolicy: Sendable {
    public var epsilon: Double
    public var ucbC: Double
    public var minPullsBeforeLearn: Int
    public var progressWeight: Double
    public var likeWeight: Double
    public var quickSkipWeight: Double

    public init(
        epsilon: Double = 0.12,
        ucbC: Double = 0.35,
        minPullsBeforeLearn: Int = 4,
        progressWeight: Double = 0.7,
        likeWeight: Double = 0.35,
        quickSkipWeight: Double = 0.55
    ) {
        self.epsilon = epsilon
        self.ucbC = ucbC
        self.minPullsBeforeLearn = minPullsBeforeLearn
        self.progressWeight = progressWeight
        self.likeWeight = likeWeight
        self.quickSkipWeight = quickSkipWeight
    }

    public func select(
        mode: DiscoveryMode,
        state: ContextualWeightPolicyState,
        isColdStart: Bool,
        featureConfidenceMean: Double = 1.0
    ) -> WeightPolicyDecision {
        let defaultArm = defaultArm(for: mode)
        let totalPulls = state.arms.values.reduce(0) { $0 + $1.pulls }
        if isColdStart || totalPulls < minPullsBeforeLearn {
            return WeightPolicyDecision(arm: defaultArm, weights: weights(for: defaultArm), explored: false)
        }
        let candidates = arms(for: mode)
        let exploreChance = epsilon * (0.5 + 0.5 * (1 - min(1, max(0, featureConfidenceMean))))
        let explore = Double.random(in: 0...1) < exploreChance
        let arm: WeightArm
        if explore {
            arm = candidates.randomElement() ?? defaultArm
        } else {
            let n = max(1, totalPulls)
            arm = candidates.max(by: { a, b in
                score(arm: a, state: state, totalPulls: n) < score(arm: b, state: state, totalPulls: n)
            }) ?? defaultArm
        }
        return WeightPolicyDecision(
            arm: arm,
            weights: weights(for: arm),
            explored: explore || arm != defaultArm
        )
    }

    public func reward(interaction: TrackInteraction, wasQuickSkip: Bool) -> Double {
        let progress = min(1, max(0, interaction.effectiveListeningProgress))
        let likeBonus: Double
        switch interaction.interactionType {
        case .explicitLike: likeBonus = 1
        case .explicitDislike, .explicitUnlike: likeBonus = -0.5
        default: likeBonus = 0
        }
        let skipPenalty = wasQuickSkip ? 1.0 : 0.0
        return min(1.5, max(-1, progressWeight * progress + likeWeight * likeBonus - quickSkipWeight * skipPenalty))
    }

    public func update(
        state: ContextualWeightPolicyState,
        arm: WeightArm,
        reward: Double
    ) -> ContextualWeightPolicyState {
        var arms = state.arms
        let current = arms[arm] ?? BanditArmStats()
        arms[arm] = current.record(reward: reward)
        return ContextualWeightPolicyState(arms: arms, lastArm: arm)
    }

    public func weights(for arm: WeightArm) -> RecommendationWeights {
        switch arm {
        case .balanced: return RecommendationWeightsPresets.balanced
        case .focused: return RecommendationWeightsPresets.focused
        case .discovery: return RecommendationWeightsPresets.discovery
        }
    }

    private func defaultArm(for mode: DiscoveryMode) -> WeightArm {
        switch mode {
        case .deepen: return .focused
        case .explore: return .discovery
        case .normal: return .balanced
        }
    }

    private func arms(for mode: DiscoveryMode) -> [WeightArm] {
        switch mode {
        case .deepen: return [.focused, .balanced]
        case .explore: return [.discovery, .balanced]
        case .normal: return [.balanced, .focused, .discovery]
        }
    }

    private func score(arm: WeightArm, state: ContextualWeightPolicyState, totalPulls: Int) -> Double {
        let stats = state.arms[arm] ?? BanditArmStats()
        if stats.pulls == 0 { return .greatestFiniteMagnitude }
        return stats.mean + ucbC * sqrt(log(Double(totalPulls)) / Double(stats.pulls))
    }
}
