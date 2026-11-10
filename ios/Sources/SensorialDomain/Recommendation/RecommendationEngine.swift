import Foundation

public struct RecommendationWeights: Sendable, Equatable {
    public var genre: Double
    public var artist: Double
    public var features: Double
    public var recentSignals: Double
    public var exploration: Double
    public var listening: Double
    public var listeningBehavior: Double
    public var contextualTime: Double
    public var similarity: Double
    public var artistDiversity: Double
    public var trackRepetition: Double
    public var artistFocusRelief: Double
    public var negativePenalty: Double
    public var coldStart: Double
    public var sessionAffinity: Double
    public var artistGraph: Double
    public var popularityFit: Double
    public var eraFit: Double
    public var localSimilarity: Double
    public var transitionFit: Double
    public var deepenRelated: Double
    public var deepenSameArtist: Double

    public init(
        genre: Double = 1.0,
        artist: Double = 0.8,
        features: Double = 0.6,
        recentSignals: Double = 0.5,
        exploration: Double = 0.7,
        listening: Double = 0.0,
        listeningBehavior: Double = 0.55,
        contextualTime: Double = 0.35,
        similarity: Double = 0.5,
        artistDiversity: Double = 0.7,
        trackRepetition: Double = 0.85,
        artistFocusRelief: Double = 1.0,
        negativePenalty: Double = 1.0,
        coldStart: Double = 0.08,
        sessionAffinity: Double = 0.45,
        artistGraph: Double = 0.5,
        popularityFit: Double = 0.35,
        eraFit: Double = 0.25,
        localSimilarity: Double = 0.4,
        transitionFit: Double = 0.45,
        deepenRelated: Double = 0.55,
        deepenSameArtist: Double = 0.7
    ) {
        self.genre = genre
        self.artist = artist
        self.features = features
        self.recentSignals = recentSignals
        self.exploration = exploration
        self.listening = listening
        self.listeningBehavior = listeningBehavior
        self.contextualTime = contextualTime
        self.similarity = similarity
        self.artistDiversity = artistDiversity
        self.trackRepetition = trackRepetition
        self.artistFocusRelief = artistFocusRelief
        self.negativePenalty = negativePenalty
        self.coldStart = coldStart
        self.sessionAffinity = sessionAffinity
        self.artistGraph = artistGraph
        self.popularityFit = popularityFit
        self.eraFit = eraFit
        self.localSimilarity = localSimilarity
        self.transitionFit = transitionFit
        self.deepenRelated = deepenRelated
        self.deepenSameArtist = deepenSameArtist
    }
}

public struct SignalScoringConfig: Sendable, Equatable {
    public var recentWindow: Int = 8
    public var targetListeningMs: Int64 = 20_000
    public var intensityLow: Double = 0.35
    public var intensityMedium: Double = 0.7
    public var intensityHigh: Double = 1.0
    public var velocityBase: Double = 0.5
    public var velocityWeight: Double = 0.5
    public var positiveRecentCoeff: Double = 0.35
    public var negativeRecentCoeff: Double = 0.4
    public var similarProximityCoeff: Double = 0.3
    public var similarNoveltyPenalty: Double = 0.15
    public var exploreNoveltyCoeff: Double = 0.35
    public var exploreProximityPenalty: Double = 0.2
    public var coldStartCenterEnergy: Double = 0.5
    public var coldStartCenterDance: Double = 0.5
    public var deepenSimilarityBoost: Double = 0.45
    public var exploreNoveltyBoost: Double = 0.45
    public var transitionWindow: Int = 5
    public var quickSkipProgress: Double = 0.12
    public var quickSkipMinMs: Int64 = 8_000

    public init() {}
}

public struct RecommendationContext: Sendable {
    public let currentTrack: Track?
    public let recentInteractions: [TrackInteraction]
    public let excludedIds: Set<TrackId>
    public let interactionTracks: [TrackId: Track]
    public let seedGenres: [GenreId]
    public let topArtists: [ArtistId]
    public let relatedArtistIds: Set<ArtistId>
    public let discoveryMode: DiscoveryMode
    public let recentTracks: [Track]
    public let localHour: Int
    public let dayOfWeek: Int
    public let sessionAffinity: SessionAffinity
    public let prefetch: Bool
    public let blockedFingerprints: Set<String>
    public let weightOverride: RecommendationWeights?

    public init(
        currentTrack: Track?,
        recentInteractions: [TrackInteraction],
        excludedIds: Set<TrackId>,
        interactionTracks: [TrackId: Track] = [:],
        seedGenres: [GenreId] = [],
        topArtists: [ArtistId] = [],
        relatedArtistIds: Set<ArtistId> = [],
        discoveryMode: DiscoveryMode = .normal,
        recentTracks: [Track] = [],
        localHour: Int = 12,
        dayOfWeek: Int = 1,
        sessionAffinity: SessionAffinity = .empty,
        prefetch: Bool = false,
        blockedFingerprints: Set<String> = [],
        weightOverride: RecommendationWeights? = nil
    ) {
        self.currentTrack = currentTrack
        self.recentInteractions = recentInteractions
        self.excludedIds = excludedIds
        self.interactionTracks = interactionTracks
        self.seedGenres = seedGenres
        self.topArtists = topArtists
        self.relatedArtistIds = relatedArtistIds
        self.discoveryMode = discoveryMode
        self.recentTracks = recentTracks
        self.localHour = localHour
        self.dayOfWeek = dayOfWeek
        self.sessionAffinity = sessionAffinity
        self.prefetch = prefetch
        self.blockedFingerprints = blockedFingerprints
        self.weightOverride = weightOverride
    }
}

public struct ScoreBreakdown: Sendable, Equatable {
    public let genre: Double
    public let artist: Double
    public let features: Double
    public let recent: Double
    public let exploration: Double
    public let listening: Double
    public let listeningBehavior: Double
    public let contextualTime: Double
    public let similarity: Double
    public let session: Double
    public let artistGraph: Double
    public let popularityFit: Double
    public let eraFit: Double
    public let localSimilarity: Double
    public let transitionFit: Double
    public let artistDiversity: Double
    public let trackRepetition: Double
    public let negative: Double
    public let coldStart: Double
    public let deepenRelated: Double
    public let deepenSameArtist: Double
    public let diversity: Double
    public let total: Double

    public init(
        genre: Double,
        artist: Double,
        features: Double,
        recent: Double,
        exploration: Double,
        listening: Double,
        listeningBehavior: Double,
        contextualTime: Double,
        similarity: Double,
        session: Double = 0,
        artistGraph: Double = 0,
        popularityFit: Double = 0,
        eraFit: Double = 0,
        localSimilarity: Double = 0,
        transitionFit: Double = 0,
        artistDiversity: Double,
        trackRepetition: Double,
        negative: Double,
        coldStart: Double,
        deepenRelated: Double = 0,
        deepenSameArtist: Double = 0,
        diversity: Double = 0,
        total: Double
    ) {
        self.genre = genre
        self.artist = artist
        self.features = features
        self.recent = recent
        self.exploration = exploration
        self.listening = listening
        self.listeningBehavior = listeningBehavior
        self.contextualTime = contextualTime
        self.similarity = similarity
        self.session = session
        self.artistGraph = artistGraph
        self.popularityFit = popularityFit
        self.eraFit = eraFit
        self.localSimilarity = localSimilarity
        self.transitionFit = transitionFit
        self.artistDiversity = artistDiversity
        self.trackRepetition = trackRepetition
        self.negative = negative
        self.coldStart = coldStart
        self.deepenRelated = deepenRelated
        self.deepenSameArtist = deepenSameArtist
        self.diversity = diversity
        self.total = total
    }
}

public struct ScoredTrack: Sendable {
    public let track: Track
    public let score: Double
    public let breakdown: ScoreBreakdown
}

public protocol RecommendationEngine: Sendable {
    func recommend(
        userProfile: UserPreferenceProfile,
        context: RecommendationContext,
        candidateTracks: [Track]
    ) -> Track?
}

public struct ScoredHeuristicRecommendationEngine: RecommendationEngine {
    private let weights: RecommendationWeights
    private let signals: SignalScoringConfig
    private let diversity: DiversityConfig
    private let preferenceConfig: PreferenceUpdateConfig
    private let trackSimilarity: LocalFeatureTrackSimilarity
    private let useModePresets: Bool
    private let useMmr: Bool

    public init(
        weights: RecommendationWeights = RecommendationWeights(),
        signals: SignalScoringConfig = SignalScoringConfig(),
        diversity: DiversityConfig = DiversityConfig(),
        preferenceConfig: PreferenceUpdateConfig = PreferenceUpdateConfig(),
        useModePresets: Bool = false,
        useMmr: Bool = true
    ) {
        self.weights = weights
        self.signals = signals
        self.diversity = diversity
        self.preferenceConfig = preferenceConfig
        self.trackSimilarity = LocalFeatureTrackSimilarity()
        self.useModePresets = useModePresets
        self.useMmr = useMmr
    }

    public func recommend(
        userProfile: UserPreferenceProfile,
        context: RecommendationContext,
        candidateTracks: [Track]
    ) -> Track? {
        rankDetailed(userProfile: userProfile, context: context, candidateTracks: candidateTracks).first?.track
    }

    public func rank(
        userProfile: UserPreferenceProfile,
        context: RecommendationContext,
        candidateTracks: [Track]
    ) -> [ScoredTrack] {
        rankDetailed(userProfile: userProfile, context: context, candidateTracks: candidateTracks)
    }

    public func rankDetailed(
        userProfile: UserPreferenceProfile,
        context: RecommendationContext,
        candidateTracks: [Track]
    ) -> [ScoredTrack] {
        let recentForDiversity = context.recentTracks.isEmpty
            ? context.recentInteractions.compactMap { context.interactionTracks[$0.trackId] }
            : context.recentTracks
        let eligible = candidateTracks.filter { candidate in
            if context.excludedIds.contains(candidate.id) { return false }
            if context.blockedFingerprints.contains(TrackFingerprint.of(candidate)) { return false }
            return !RecentVersionBlock.isBlocked(
                candidate: candidate,
                recentTracks: recentForDiversity,
                currentTrack: context.currentTrack,
                config: diversity
            )
        }
        guard !eligible.isEmpty else { return [] }
        let focus = ArtistFocusDetector.detect(
            recentInteractions: context.recentInteractions,
            interactionTracks: context.interactionTracks,
            config: diversity
        )
        let focusedArtistId = context.discoveryMode == .deepen ? nil : focus.focusedArtistId
        let activeWeights = context.weightOverride
            ?? (useModePresets ? RecommendationWeightsPresets.forMode(context.discoveryMode) : weights)
        let relevanceRanked = eligible
            .map {
                scoreDetailed(
                    track: $0,
                    profile: userProfile,
                    context: context,
                    focusedArtistId: focusedArtistId,
                    activeWeights: activeWeights
                )
            }
            .sorted { $0.score > $1.score }
        guard useMmr else { return relevanceRanked }
        var seed: [Track] = []
        if let current = context.currentTrack { seed.append(current) }
        seed.append(contentsOf: recentForDiversity.suffix(4))
        var seen = Set<TrackId>()
        seed = seed.filter { seen.insert($0.id).inserted }
        return MmrSelector.selectSlate(
            ranked: relevanceRanked,
            seedSelected: seed,
            mode: context.discoveryMode,
            trackSimilarity: trackSimilarity
        )
    }

    public func scoreDetailed(
        track: Track,
        profile: UserPreferenceProfile,
        context: RecommendationContext,
        focusedArtistId: ArtistId? = nil,
        activeWeights: RecommendationWeights? = nil
    ) -> ScoredTrack {
        let w = activeWeights ?? weights
        let genreAffinity = unitSigned(average(track.genres.map { profile.genreScore($0) }))
        let artistAffinity = unitSigned(average(track.artists.map { profile.artistScore($0.id) }))
        let featureAffinityValue = unit01(
            featureAffinity(preferred: profile.preferredFeatures, candidate: track.audioFeatures)
                * min(1, max(0.25, track.featureConfidence))
        )
        let recent = unitSigned(recentGestureSignal(candidate: track, context: context))
        let exploration = unit01(
            explorationAlignment(
                track: track,
                context: context,
                explorationPreference: profile.explorationPreference
            ) + context.sessionAffinity.explorationBoost * 0.5
        )
        let listeningBehavior = unitSigned(
            average(track.genres.map { profile.genreListeningScore($0) })
                + average(track.artists.map { profile.artistListeningScore($0.id) }) * 0.5
        )
        let contextual = unitSigned(
            average(
                track.genres.map {
                    profile.contextualGenreScore(
                        $0,
                        hour: context.localHour,
                        dayOfWeek: context.dayOfWeek,
                        config: preferenceConfig
                    )
                }
            )
        )
        let similarity = unit01(currentSimilarity(candidate: track, current: context.currentTrack))
        let session = unitSigned(
            average(track.genres.map { context.sessionAffinity.genreBoost($0) })
                + average(track.artists.map { context.sessionAffinity.artistBoost($0.id) }) * 0.7
        )
        let recentTracks = context.recentTracks.isEmpty
            ? context.recentInteractions.compactMap { context.interactionTracks[$0.trackId] }
            : context.recentTracks
        let currentPrimary = context.currentTrack?.primaryArtist.id
        let sameArtistStreak = SameArtistStreak.count(
            primaryArtistId: currentPrimary,
            recentTracks: recentTracks,
            currentTrack: context.currentTrack
        )
        let sharesCurrentArtist = currentPrimary.map { primary in
            track.artists.contains { $0.id == primary }
        } ?? false
        let isRelatedArtist = track.artists.contains { context.relatedArtistIds.contains($0.id) }
        let graph = unitSigned(artistGraphProximity(candidate: track, context: context, sameArtistStreak: sameArtistStreak))
        let popularity = unit01(popularityFit(track: track, mode: context.discoveryMode))
        let era = unit01(eraFit(candidate: track, recent: recentTracks))
        let localSimRaw: Double
        if let current = context.currentTrack {
            localSimRaw = trackSimilarity.similarity(track, current)
                * min(1, max(0.25, track.featureConfidence))
        } else {
            localSimRaw = 0
        }
        let localSimScale: Double
        switch context.discoveryMode {
        case .explore: localSimScale = 0.2
        case .deepen: localSimScale = 1
        case .normal: localSimScale = 0.65
        }
        let localSim = unit01(localSimRaw * localSimScale)
        let transition = unit01(transitionFit(candidate: track, recentTracks: recentTracks, current: context.currentTrack))
        let artistPenalty = unit01(
            RecentArtistPenalty.score(
                candidate: track,
                recentTracks: recentTracks,
                config: diversity,
                focusedArtistId: focusedArtistId
            )
        )
        let trackPenalty = unit01(
            RecentTrackPenalty.score(candidate: track, recentTracks: recentTracks, config: diversity)
        )
        let negative = unit01(negativePressure(genreScore: genreAffinity, artistScore: artistAffinity))
        let cold = profile.isColdStart ? unit01(coldStartTieBreak(track: track)) : 0

        let deepenExtra = Double(min(3, context.sessionAffinity.deepenStreak)) * 0.08
        let exploreExtra = Double(min(3, context.sessionAffinity.exploreStreak)) * 0.08
        let deepenSimilarityScale: Double
        if context.discoveryMode == .deepen && sharesCurrentArtist {
            if sameArtistStreak >= 3 { deepenSimilarityScale = 0.35 }
            else if sameArtistStreak >= 2 { deepenSimilarityScale = 0.55 }
            else { deepenSimilarityScale = 1 }
        } else {
            deepenSimilarityScale = 1
        }

        let modeSimilarityBoost: Double
        let modeExplorationBoost: Double
        switch context.discoveryMode {
        case .deepen:
            modeSimilarityBoost = (signals.deepenSimilarityBoost + deepenExtra) * similarity * deepenSimilarityScale
            modeExplorationBoost = sameArtistStreak >= 2
                ? 0.12 * (1 - similarity)
                : -0.15 * (1 - similarity)
        case .explore:
            modeSimilarityBoost = -signals.exploreNoveltyBoost * similarity * 0.35
            modeExplorationBoost = (signals.exploreNoveltyBoost + exploreExtra) * (1 - similarity)
        case .normal:
            modeSimilarityBoost = 0
            modeExplorationBoost = context.sessionAffinity.explorationBoost * (1 - similarity) * 0.4
        }

        let deepenSameArtistRaw: Double
        if context.discoveryMode == .deepen && sharesCurrentArtist && sameArtistStreak >= 2 {
            deepenSameArtistRaw = min(1, Double(sameArtistStreak - 1) * 0.35)
        } else {
            deepenSameArtistRaw = 0
        }
        let deepenRelatedRaw: Double
        if context.discoveryMode == .deepen &&
            isRelatedArtist &&
            !sharesCurrentArtist &&
            sameArtistStreak >= 2 {
            deepenRelatedRaw = min(1, 0.55 + Double(min(3, sameArtistStreak - 2)) * 0.15)
        } else {
            deepenRelatedRaw = 0
        }

        let genre = w.genre * genreAffinity
        let artist = w.artist * artistAffinity
        let features = w.features * featureAffinityValue
        let recentWeighted = w.recentSignals * recent
        let explorationWeighted = w.exploration * min(1.5, max(-1, exploration + modeExplorationBoost))
        let listeningBehaviorWeighted = w.listeningBehavior * listeningBehavior
        let contextualWeighted = w.contextualTime * contextual
        let similarityWeighted = w.similarity * min(1.5, max(-1, similarity + modeSimilarityBoost))
        let sessionWeighted = w.sessionAffinity * session
        let graphWeighted = w.artistGraph * graph
        let popularityWeighted = w.popularityFit * popularity
        let eraWeighted = w.eraFit * era
        let localSimWeighted = w.localSimilarity * localSim
        let transitionWeighted = w.transitionFit * transition
        let artistDivWeighted = w.artistDiversity * artistPenalty
        let trackRepWeighted = w.trackRepetition * trackPenalty
        let negativeWeighted = w.negativePenalty * negative
        let coldWeighted = w.coldStart * cold
        let deepenRelatedWeighted = w.deepenRelated * deepenRelatedRaw
        let deepenSameArtistWeighted = w.deepenSameArtist * deepenSameArtistRaw

        let total = genre + artist + features + recentWeighted + explorationWeighted
            + listeningBehaviorWeighted + contextualWeighted + similarityWeighted
            + sessionWeighted + graphWeighted + popularityWeighted + eraWeighted + localSimWeighted
            + transitionWeighted + deepenRelatedWeighted
            - artistDivWeighted - trackRepWeighted - negativeWeighted + coldWeighted
            - deepenSameArtistWeighted

        return ScoredTrack(
            track: track,
            score: total,
            breakdown: ScoreBreakdown(
                genre: genre,
                artist: artist,
                features: features,
                recent: recentWeighted,
                exploration: explorationWeighted,
                listening: 0,
                listeningBehavior: listeningBehaviorWeighted,
                contextualTime: contextualWeighted,
                similarity: similarityWeighted,
                session: sessionWeighted,
                artistGraph: graphWeighted,
                popularityFit: popularityWeighted,
                eraFit: eraWeighted,
                localSimilarity: localSimWeighted,
                transitionFit: transitionWeighted,
                artistDiversity: -artistDivWeighted,
                trackRepetition: -trackRepWeighted,
                negative: -negativeWeighted,
                coldStart: coldWeighted,
                deepenRelated: deepenRelatedWeighted,
                deepenSameArtist: -deepenSameArtistWeighted,
                diversity: 0,
                total: total
            )
        )
    }

    private func transitionFit(candidate: Track, recentTracks: [Track], current: Track?) -> Double {
        var window: [Track] = []
        if let current { window.append(current) }
        window.append(contentsOf: recentTracks.reversed())
        var seen = Set<TrackId>()
        window = window.filter { seen.insert($0.id).inserted }.prefix(signals.transitionWindow).map { $0 }
        guard !window.isEmpty else { return 0 }
        var weighted = 0.0
        var weightSum = 0.0
        for (index, track) in window.enumerated() {
            let w = 1.0 / (1.0 + Double(index))
            weighted += w * trackAffinity(candidate, track)
            weightSum += w
        }
        return weightSum == 0 ? 0 : min(1, max(0, weighted / weightSum))
    }

    private func artistGraphProximity(candidate: Track, context: RecommendationContext, sameArtistStreak: Int) -> Double {
        guard let current = context.currentTrack else { return 0 }
        let candidateArtists = Set(candidate.artists.map(\.id))
        let currentArtists = Set(current.artists.map(\.id))
        if !candidateArtists.isDisjoint(with: currentArtists) {
            switch context.discoveryMode {
            case .deepen:
                if sameArtistStreak >= 3 { return 0.1 }
                if sameArtistStreak >= 2 { return 0.35 }
                return 0.85
            case .explore: return -0.25
            case .normal: return 0.15
            }
        }
        if !candidateArtists.isDisjoint(with: context.relatedArtistIds) {
            switch context.discoveryMode {
            case .deepen: return sameArtistStreak >= 2 ? 0.95 : 0.7
            case .explore: return 0.2
            case .normal: return 0.45
            }
        }
        if !candidateArtists.isDisjoint(with: Set(context.topArtists)) {
            return context.discoveryMode == .explore ? 0.15 : 0.4
        }
        return context.discoveryMode == .explore ? 0.35 : 0
    }

    private func popularityFit(track: Track, mode: DiscoveryMode) -> Double {
        let p = Double(min(100, max(0, track.popularity))) / 100.0
        switch mode {
        case .deepen: return 0.35 + 0.65 * p
        case .explore: return 1 - abs(p - 0.4)
        case .normal: return 0.5 + 0.3 * p
        }
    }

    private func eraFit(candidate: Track, recent: [Track]) -> Double {
        guard let year = candidate.releaseYear else { return 0 }
        let recentYears = recent.compactMap(\.releaseYear)
        guard !recentYears.isEmpty else { return 0 }
        let mean = Double(recentYears.reduce(0, +)) / Double(recentYears.count)
        let distance = abs(Double(year) - mean)
        return 1 - min(1, max(0, distance / 25))
    }

    private func currentSimilarity(candidate: Track, current: Track?) -> Double {
        guard let current else { return 0 }
        return trackAffinity(candidate, current)
    }

    private func recentGestureSignal(candidate: Track, context: RecommendationContext) -> Double {
        let recent = Array(context.recentInteractions.suffix(signals.recentWindow))
        guard !recent.isEmpty else { return 0 }
        var score = 0.0
        for (index, interaction) in recent.enumerated() {
            guard let interacted = context.interactionTracks[interaction.trackId] else { continue }
            let recency = Double(index + 1) / Double(recent.count)
            let strength = signalStrength(interaction)
            let affinity = trackAffinity(candidate, interacted)
            let progress = interaction.effectiveListeningProgress
            let duration = interaction.trackDurationMs ?? interacted.durationMs
            let quickSkip = QuickSkip.isQuickSkip(
                progress: progress,
                trackDurationMs: duration,
                baseProgress: signals.quickSkipProgress,
                minListeningMs: signals.quickSkipMinMs
            )

            switch interaction.interactionType {
            case .explicitLike:
                score += signals.positiveRecentCoeff * affinity * strength * recency
            case .explicitUnlike:
                score -= signals.positiveRecentCoeff * affinity * strength * recency
            case .explicitDislike:
                score -= signals.negativeRecentCoeff * affinity * strength * recency
            case .navigationAdvance, .discovery:
                if quickSkip {
                    score -= signals.negativeRecentCoeff * affinity * 0.85 * recency
                } else {
                    score += (progress - 0.45) * 0.25 * affinity * recency
                }
            case .navigationReturn:
                score += 0.2 * affinity * recency
            case .preference:
                switch interaction.verticalIntent {
                case .positive:
                    score += signals.positiveRecentCoeff * affinity * strength * recency
                case .negative:
                    score -= signals.negativeRecentCoeff * affinity * strength * recency
                case .none:
                    break
                }
            }

            let novelty = noveltyBetween(candidate, interacted)
            let proximity = 1 - novelty
            if quickSkip &&
                (interaction.interactionType == .navigationAdvance || interaction.interactionType == .discovery) {
                score -= signals.similarProximityCoeff * proximity * 0.9 * recency
            }
            let horizontal: HorizontalIntent
            switch interaction.discoveryMode {
            case .deepen: horizontal = .similar
            case .explore: horizontal = .explore
            case .normal:
                if interaction.horizontalIntent != .none && interaction.interactionType == .preference {
                    horizontal = interaction.horizontalIntent
                } else {
                    horizontal = .none
                }
            }
            switch horizontal {
            case .similar:
                score += (signals.similarProximityCoeff * proximity - signals.similarNoveltyPenalty * novelty) * strength * recency
            case .explore:
                score += (signals.exploreNoveltyCoeff * novelty - signals.exploreProximityPenalty * proximity) * strength * recency
            case .none:
                break
            }
        }
        return score
    }

    private func explorationAlignment(track: Track, context: RecommendationContext, explorationPreference: Double) -> Double {
        guard let current = context.currentTrack else { return 0 }
        let noveltyVsCurrent = noveltyBetween(track, current)
        let recentGenres = Set(
            context.recentInteractions
                .suffix(signals.recentWindow)
                .compactMap { context.interactionTracks[$0.trackId] }
                .flatMap(\.genres)
        )
        let genreDiversity: Double
        if recentGenres.isEmpty {
            genreDiversity = noveltyVsCurrent
        } else {
            let overlap = Double(track.genres.filter { recentGenres.contains($0) }.count)
            genreDiversity = 1 / (1 + overlap)
        }
        let similarPull = (1 - noveltyVsCurrent) * featureAffinity(preferred: current.audioFeatures, candidate: track.audioFeatures)
        let explorePull = 0.6 * noveltyVsCurrent + 0.4 * genreDiversity
        let modeBias: Double
        switch context.discoveryMode {
        case .deepen: modeBias = 0.2
        case .explore: modeBias = 0.8
        case .normal: modeBias = explorationPreference
        }
        return modeBias * explorePull + (1 - modeBias) * similarPull
    }

    private func signalStrength(_ interaction: TrackInteraction) -> Double {
        let intensity: Double
        switch interaction.gestureIntensity {
        case .low: intensity = signals.intensityLow
        case .medium: intensity = signals.intensityMedium
        case .high: intensity = signals.intensityHigh
        }
        let velocity = signals.velocityBase + signals.velocityWeight * interaction.gestureVelocity
        let listening: Double
        if interaction.effectiveListeningProgress > 0 {
            listening = interaction.effectiveListeningProgress
        } else {
            listening = min(1, Double(interaction.listeningDurationMs) / Double(signals.targetListeningMs))
        }
        return intensity * velocity * listening
    }

    private func trackAffinity(_ a: Track, _ b: Track) -> Double {
        let sharedGenres = Double(Set(a.genres).intersection(Set(b.genres)).count)
        let sharedArtists = Double(Set(a.artists.map(\.id)).intersection(Set(b.artists.map(\.id))).count)
        let genrePart = sharedGenres / Double(max(1, a.genres.count))
        let artistPart = sharedArtists > 0 ? 1.0 : 0.0
        let features = featureAffinity(preferred: a.audioFeatures, candidate: b.audioFeatures)
        return min(1, max(0, 0.45 * genrePart + 0.25 * artistPart + 0.3 * features))
    }

    private func noveltyBetween(_ a: Track, _ b: Track) -> Double {
        let sharedGenres = Set(a.genres).intersection(Set(b.genres)).count
        let sharedArtists = Set(a.artists.map(\.id)).intersection(Set(b.artists.map(\.id))).count
        return 1 / (1 + Double(sharedGenres + sharedArtists))
    }

    private func featureAffinity(preferred: AudioFeatures?, candidate: AudioFeatures) -> Double {
        guard let preferred else { return 0 }
        let energy = 1 - abs(preferred.energy - candidate.energy)
        let dance = 1 - abs(preferred.danceability - candidate.danceability)
        let valence = 1 - abs(preferred.valence - candidate.valence)
        let tempo = 1 - min(1, abs(preferred.tempo - candidate.tempo) / 80)
        return (energy + dance + valence + tempo) / 4
    }

    private func negativePressure(genreScore: Double, artistScore: Double) -> Double {
        let genreNeg = genreScore < 0 ? -genreScore : 0
        let artistNeg = artistScore < 0 ? -artistScore : 0
        return genreNeg + artistNeg
    }

    private func coldStartTieBreak(track: Track) -> Double {
        let hash = Double(abs(track.id.value.hashValue % 10_000)) / 10_000.0
        let center = 1 - (
            abs(track.audioFeatures.energy - signals.coldStartCenterEnergy) +
            abs(track.audioFeatures.danceability - signals.coldStartCenterDance)
        ) / 2
        return 0.55 * center + 0.45 * hash
    }

    private func average(_ values: [Double]) -> Double {
        guard !values.isEmpty else { return 0 }
        return values.reduce(0, +) / Double(values.count)
    }

    private func unitSigned(_ value: Double) -> Double { min(1, max(-1, value)) }
    private func unit01(_ value: Double) -> Double { min(1, max(0, value)) }
}

private extension UserPreferenceProfile {
    var isColdStart: Bool {
        positiveSignalCount + negativeSignalCount == 0
            && genreScores.isEmpty
            && artistScores.isEmpty
            && genreListeningScores.isEmpty
    }
}
