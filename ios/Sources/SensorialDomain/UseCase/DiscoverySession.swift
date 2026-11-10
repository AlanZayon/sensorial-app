import Foundation

public actor DiscoverySession {
    private let musicProvider: any MusicProvider
    private let historyRepository: any PlaybackHistoryRepository
    private let interactionRepository: any InteractionRepository
    private let preferenceRepository: any UserPreferenceRepository
    private let recommendationEngine: any RecommendationEngine
    private let gestureInterpreter: GestureInterpreter
    private let nowEpochMs: @Sendable () -> Int64
    private let newInteractionId: @Sendable () -> InteractionId

    private var listeningStartedAtMs: Int64
    private var likedTrackIds: Set<TrackId> = []
    private var sessionAffinity: SessionAffinity = .empty
    private var prefetchedCandidates: [Track] = []
    private var prefetchedForMode: DiscoveryMode = .normal
    private var lastRelatedArtistIds: Set<ArtistId> = []
    private var weightPolicy = ContextualWeightPolicy()
    private var banditState = ContextualWeightPolicyState.empty
    private var pendingWeightArm: WeightArm?
    private var stateContinuation: AsyncStream<FeedState>.Continuation?
    public private(set) var state: FeedState = FeedState()

    public var stateStream: AsyncStream<FeedState> {
        AsyncStream { continuation in
            self.stateContinuation = continuation
            continuation.yield(self.state)
        }
    }

    public init(
        musicProvider: any MusicProvider,
        historyRepository: any PlaybackHistoryRepository,
        interactionRepository: any InteractionRepository,
        preferenceRepository: any UserPreferenceRepository,
        recommendationEngine: any RecommendationEngine = ScoredHeuristicRecommendationEngine(),
        gestureInterpreter: GestureInterpreter = GestureInterpreter(),
        nowEpochMs: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) },
        newInteractionId: @escaping @Sendable () -> InteractionId = { InteractionId(UUID().uuidString) }
    ) {
        self.musicProvider = musicProvider
        self.historyRepository = historyRepository
        self.interactionRepository = interactionRepository
        self.preferenceRepository = preferenceRepository
        self.recommendationEngine = recommendationEngine
        self.gestureInterpreter = gestureInterpreter
        self.nowEpochMs = nowEpochMs
        self.newInteractionId = newInteractionId
        self.listeningStartedAtMs = nowEpochMs()
    }

    public func start() async {
        publish(FeedState(status: .loading))
        await hydrateLikedTrackIds()
        do {
            if let existing = await historyRepository.current() {
                do {
                    try await musicProvider.playTrack(id: existing.id)
                    listeningStartedAtMs = nowEpochMs()
                    await publishPlaying(lastIntentLabel: nil)
                    Task { await self.warmPrefetch(current: existing) }
                    return
                } catch {}
            }

            let profile = await preferenceRepository.get()
            let seedGenres = profile.topPositiveGenres()
            let excluded = Set((await historyRepository.snapshot()).tracks.map(\.id))
            let pool = try await musicProvider.getCandidatePool(
                context: RecommendationContext(
                    currentTrack: nil,
                    recentInteractions: [],
                    excludedIds: excluded,
                    seedGenres: seedGenres,
                    topArtists: profile.topPositiveArtists(),
                    discoveryMode: .normal,
                    sessionAffinity: sessionAffinity
                )
            )
            let candidates = pool.tracks
            guard !candidates.isEmpty else {
                publish(FeedState(status: .noRecommendations))
                return
            }
            var lastError: Error?
            for seed in candidates {
                do {
                    await historyRepository.reset(seed: seed)
                    try await musicProvider.playTrack(id: seed.id)
                    listeningStartedAtMs = nowEpochMs()
                    await publishPlaying(lastIntentLabel: nil)
                    Task { await self.warmPrefetch(current: seed) }
                    return
                } catch {
                    lastError = error
                }
            }
            throw lastError ?? URLError(.resourceUnavailable)
        } catch {
            publish(FeedState(status: .error, errorMessage: error.localizedDescription))
        }
    }

    public func goBack() async {
        guard await historyRepository.canGoBack() else { return }
        publish(state.with(status: .changingTrack))
        do {
            await closeListening(action: .returnBack, mode: .normal, type: .navigationReturn)
            let track = try await historyRepository.previous()
            try await musicProvider.playTrack(id: track.id)
            listeningStartedAtMs = nowEpochMs()
            await publishPlaying(lastIntentLabel: "Anterior")
        } catch {
            publish(FeedState(status: .error, errorMessage: error.localizedDescription))
        }
    }

    public func goToPrevious() async { await goBack() }

    public func advance() async {
        if await historyRepository.canGoForward() {
            await goToNextInHistory()
        } else {
            await discover(mode: .normal, label: "Próxima")
        }
    }

    public func goToNextInHistory() async {
        guard await historyRepository.canGoForward() else { return }
        publish(state.with(status: .changingTrack))
        do {
            await closeListening(action: .advance, mode: .normal, type: .navigationAdvance)
            let track = try await historyRepository.next()
            try await musicProvider.playTrack(id: track.id)
            listeningStartedAtMs = nowEpochMs()
            await publishPlaying(lastIntentLabel: "Próxima")
        } catch {
            publish(FeedState(status: .error, errorMessage: error.localizedDescription))
        }
    }

    public func discover(mode: DiscoveryMode, label: String? = nil) async {
        let intentLabel = label ?? {
            switch mode {
            case .normal: return "Próxima"
            case .deepen: return "Aprofundar"
            case .explore: return "Explorar"
            }
        }()
        guard let current = await historyRepository.current() else { return }
        publish(state.with(status: .changingTrack, lastIntentLabel: intentLabel))
        await closeListening(
            action: mode == .normal ? .advance : .none,
            mode: mode,
            type: mode == .normal ? .navigationAdvance : .discovery
        )

        let recent = await interactionRepository.recent(limit: 20)
        let snapshot = await historyRepository.snapshot()
        let interactions = await interactionRepository.all()
        let heardIds = DiscoveryExclusions.wellHeardIds(
            interactions: interactions,
            historyEntries: snapshot.entries
        )
        let excluded = DiscoveryExclusions.ids(
            likedIds: likedTrackIds,
            interactions: interactions,
            historyEntries: snapshot.entries,
            extra: [current.id]
        )
        let knownTracks = snapshot.tracks + [current]
        let blockedFingerprints = DiscoveryExclusions.fingerprints(
            knownTracks: knownTracks,
            likedIds: likedTrackIds,
            heardIds: heardIds
        )
        let profile = await preferenceRepository.get()
        do {
            let seedGenres = profile.topPositiveGenres()
            let topArtists = profile.topPositiveArtists()
            let cacheUsable = !prefetchedCandidates.isEmpty &&
                (prefetchedForMode == mode || mode == .normal)
            let cached = cacheUsable
                ? prefetchedCandidates.filter {
                    !excluded.contains($0.id) &&
                        !blockedFingerprints.contains(TrackFingerprint.of($0))
                }
                : []
            var relatedIds = lastRelatedArtistIds
            var candidates = cached

            var next: Track?
            if !cached.isEmpty {
                let interactionTracks = await resolveInteractionTracks(
                    recent: recent,
                    candidates: cached,
                    historyTracks: snapshot.tracks,
                    current: current
                )
                let context = makeRankingContext(
                    current: current,
                    recent: recent,
                    excluded: excluded,
                    blockedFingerprints: blockedFingerprints,
                    interactionTracks: interactionTracks,
                    seedGenres: seedGenres,
                    topArtists: topArtists,
                    relatedIds: relatedIds,
                    mode: mode,
                    historyTracks: snapshot.tracks,
                    profile: profile,
                    candidates: cached
                )
                if let cachedPick = recommendationEngine.recommend(
                    userProfile: profile,
                    context: context,
                    candidateTracks: cached
                ) {
                    prefetchedCandidates = cached.filter { $0.id != cachedPick.id }
                    next = cachedPick
                }
            }

            if next == nil {
                let draft = RecommendationContext(
                    currentTrack: current,
                    recentInteractions: recent,
                    excludedIds: excluded,
                    seedGenres: seedGenres,
                    topArtists: topArtists,
                    discoveryMode: mode,
                    recentTracks: snapshot.tracks,
                    sessionAffinity: sessionAffinity,
                    prefetch: false,
                    blockedFingerprints: blockedFingerprints
                )
                let pool = try await musicProvider.getCandidatePool(context: draft)
                candidates = pool.tracks
                relatedIds = pool.relatedArtistIds
                lastRelatedArtistIds = relatedIds
                let interactionTracks = await resolveInteractionTracks(
                    recent: recent,
                    candidates: candidates,
                    historyTracks: snapshot.tracks,
                    current: current
                )
                let context = makeRankingContext(
                    current: current,
                    recent: recent,
                    excluded: excluded,
                    blockedFingerprints: blockedFingerprints,
                    interactionTracks: interactionTracks,
                    seedGenres: seedGenres,
                    topArtists: topArtists,
                    relatedIds: relatedIds,
                    mode: mode,
                    historyTracks: snapshot.tracks,
                    profile: profile,
                    candidates: candidates
                )
                guard let picked = recommendationEngine.recommend(
                    userProfile: profile,
                    context: context,
                    candidateTracks: candidates
                ) else {
                    publish(
                        FeedState(
                            status: .noRecommendations,
                            currentTrack: current,
                            canGoBack: snapshot.canGoBack,
                            canGoForward: snapshot.canGoForward,
                            lastIntentLabel: intentLabel
                        )
                    )
                    return
                }
                prefetchedCandidates = candidates.filter { $0.id != picked.id && !excluded.contains($0.id) }
                next = picked
            }

            guard let next else { return }
            await historyRepository.push(
                next,
                discoveryMode: mode,
                recommendedAtEpochMs: nowEpochMs()
            )
            Task { await self.warmPrefetch(current: next, mode: mode) }
            try await musicProvider.playTrack(id: next.id)
            listeningStartedAtMs = nowEpochMs()
            await publishPlaying(lastIntentLabel: intentLabel)
        } catch {
            publish(FeedState(status: .error, errorMessage: error.localizedDescription))
        }
    }

    private func makeRankingContext(
        current: Track,
        recent: [TrackInteraction],
        excluded: Set<TrackId>,
        blockedFingerprints: Set<String>,
        interactionTracks: [TrackId: Track],
        seedGenres: [GenreId],
        topArtists: [ArtistId],
        relatedIds: Set<ArtistId>,
        mode: DiscoveryMode,
        historyTracks: [Track],
        profile: UserPreferenceProfile,
        candidates: [Track]
    ) -> RecommendationContext {
        let isCold = profile.positiveSignalCount + profile.negativeSignalCount == 0
            && profile.genreScores.isEmpty && profile.artistScores.isEmpty
        let featureMean = candidates.isEmpty
            ? 1.0
            : candidates.map(\.featureConfidence).reduce(0, +) / Double(candidates.count)
        let decision = weightPolicy.select(
            mode: mode,
            state: banditState,
            isColdStart: isCold,
            featureConfidenceMean: featureMean
        )
        pendingWeightArm = decision.arm
        return RecommendationContext(
            currentTrack: current,
            recentInteractions: recent,
            excludedIds: excluded,
            interactionTracks: interactionTracks,
            seedGenres: seedGenres,
            topArtists: topArtists,
            relatedArtistIds: relatedIds,
            discoveryMode: mode,
            recentTracks: historyTracks,
            sessionAffinity: sessionAffinity,
            blockedFingerprints: blockedFingerprints,
            weightOverride: decision.weights
        )
    }

    /// Toggle explicit like — second tap undoes preference impact.
    public func explicitLike() async {
        guard let current = await historyRepository.current() else { return }
        if likedTrackIds.contains(current.id) {
            let interaction = buildInteraction(
                track: current,
                type: .explicitUnlike,
                mode: .normal,
                action: .none,
                intensity: .high,
                velocity: 1
            )
            await interactionRepository.append(interaction)
            let updated = await preferenceRepository.get().applying(track: current, interaction: interaction)
            await preferenceRepository.save(updated)
            likedTrackIds.remove(current.id)
            publish(state.with(lastIntentLabel: "Curtida removida", isCurrentTrackLiked: false))
            return
        }
        let interaction = buildInteraction(
            track: current,
            type: .explicitLike,
            mode: .normal,
            action: .none,
            intensity: .high,
            velocity: 1
        )
        await interactionRepository.append(interaction)
        let updated = await preferenceRepository.get().applying(track: current, interaction: interaction)
        await preferenceRepository.save(updated)
        likedTrackIds.insert(current.id)
        publish(state.with(lastIntentLabel: "Gostei", isCurrentTrackLiked: true))
    }

    public func explicitDislike() async {
        guard let current = await historyRepository.current() else { return }
        let interaction = buildInteraction(
            track: current,
            type: .explicitDislike,
            mode: .normal,
            action: .none,
            intensity: .high,
            velocity: 1
        )
        await interactionRepository.append(interaction)
        let updated = await preferenceRepository.get().applying(track: current, interaction: interaction)
        await preferenceRepository.save(updated)
        publish(state.with(lastIntentLabel: "Não quero"))
    }

    public func togglePause() async {
        switch state.status {
        case .playing:
            await musicProvider.pausePlayback()
            publish(state.with(status: .paused))
        case .paused:
            await musicProvider.resumePlayback()
            publish(state.with(status: .playing))
        default:
            break
        }
    }

    public func interpretAndHandle(_ input: RawGestureInput) async {
        switch gestureInterpreter.interpret(input) {
        case .navigateBack:
            await goBack()
        case .navigateForward:
            await advance()
        case let .discover(mode):
            await discover(mode: mode)
        case .invalid:
            break
        }
    }

    private func closeListening(action: NavigationAction, mode: DiscoveryMode, type: InteractionType) async {
        guard let current = await historyRepository.current() else { return }
        let interaction = buildInteraction(
            track: current,
            type: type,
            mode: mode,
            action: action,
            intensity: .medium,
            velocity: 0.5
        )
        await interactionRepository.append(interaction)
        let updated = await preferenceRepository.get().applying(track: current, interaction: interaction)
        await preferenceRepository.save(updated)
        sessionAffinity = sessionAffinity.applying(track: current, interaction: interaction)
        if let arm = pendingWeightArm {
            let wasQuickSkip = QuickSkip.isQuickSkip(interaction: interaction, trackDurationMs: current.durationMs)
            let reward = weightPolicy.reward(interaction: interaction, wasQuickSkip: wasQuickSkip)
            banditState = weightPolicy.update(state: banditState, arm: arm, reward: reward)
            pendingWeightArm = nil
        }
    }

    private func buildInteraction(
        track: Track,
        type: InteractionType,
        mode: DiscoveryMode,
        action: NavigationAction,
        intensity: GestureIntensity,
        velocity: Double
    ) -> TrackInteraction {
        let listened = max(0, nowEpochMs() - listeningStartedAtMs)
        let progress: Double
        if track.durationMs > 0 {
            progress = min(1, max(0, Double(listened) / Double(track.durationMs)))
        } else {
            progress = 0
        }
        let comps = Calendar.current.dateComponents([.hour, .weekday], from: Date(timeIntervalSince1970: Double(nowEpochMs()) / 1000))
        let hour = comps.hour ?? 12
        let weekday = comps.weekday ?? 1
        let isoDow = weekday == 1 ? 7 : weekday - 1
        return TrackInteraction(
            id: newInteractionId(),
            trackId: track.id,
            interactionType: type,
            gestureIntensity: intensity,
            gestureVelocity: min(1, max(0, velocity)),
            listeningDurationMs: listened,
            timestampEpochMs: nowEpochMs(),
            artistId: track.primaryArtist.id,
            trackDurationMs: track.durationMs,
            listeningProgress: progress,
            discoveryMode: mode,
            localHour: hour,
            dayOfWeek: isoDow,
            navigationAction: action
        )
    }

    private func publishPlaying(lastIntentLabel: String?) async {
        let snapshot = await historyRepository.snapshot()
        publish(
            FeedState(
                status: .playing,
                currentTrack: snapshot.currentTrack,
                canGoBack: snapshot.canGoBack,
                canGoForward: snapshot.canGoForward,
                lastIntentLabel: lastIntentLabel,
                playbackMode: musicProvider.playbackMode(),
                isCurrentTrackLiked: snapshot.currentTrack.map { likedTrackIds.contains($0.id) } ?? false
            )
        )
    }

    private func hydrateLikedTrackIds() async {
        likedTrackIds.removeAll()
        let recent = await interactionRepository.recent(limit: 500)
        for interaction in recent.sorted(by: { $0.timestampEpochMs < $1.timestampEpochMs }) {
            switch interaction.interactionType {
            case .explicitLike:
                likedTrackIds.insert(interaction.trackId)
            case .explicitUnlike:
                likedTrackIds.remove(interaction.trackId)
            default:
                break
            }
        }
    }

    private func publish(_ newState: FeedState) {
        state = newState
        stateContinuation?.yield(newState)
    }

    private func warmPrefetch(current: Track, mode: DiscoveryMode = .normal) async {
        do {
            let profile = await preferenceRepository.get()
            let recent = await interactionRepository.recent(limit: 20)
            let snapshot = await historyRepository.snapshot()
            let interactions = await interactionRepository.all()
            let heardIds = DiscoveryExclusions.wellHeardIds(
                interactions: interactions,
                historyEntries: snapshot.entries
            )
            let excluded = DiscoveryExclusions.ids(
                likedIds: likedTrackIds,
                interactions: interactions,
                historyEntries: snapshot.entries,
                extra: [current.id]
            )
            let blockedFingerprints = DiscoveryExclusions.fingerprints(
                knownTracks: snapshot.tracks + [current],
                likedIds: likedTrackIds,
                heardIds: heardIds
            )
            let pool = try await musicProvider.getCandidatePool(
                context: RecommendationContext(
                    currentTrack: current,
                    recentInteractions: recent,
                    excludedIds: excluded,
                    seedGenres: profile.topPositiveGenres(),
                    topArtists: profile.topPositiveArtists(),
                    discoveryMode: mode,
                    recentTracks: snapshot.tracks,
                    sessionAffinity: sessionAffinity,
                    prefetch: true,
                    blockedFingerprints: blockedFingerprints
                )
            )
            guard await historyRepository.current()?.id == current.id else { return }
            prefetchedCandidates = pool.tracks
            prefetchedForMode = mode
            lastRelatedArtistIds = pool.relatedArtistIds
        } catch {
            // Prefetch is best-effort.
        }
    }

    private func resolveInteractionTracks(
        recent: [TrackInteraction],
        candidates: [Track],
        historyTracks: [Track],
        current: Track
    ) async -> [TrackId: Track] {
        var known: [TrackId: Track] = [:]
        (historyTracks + candidates + [current]).forEach { known[$0.id] = $0 }
        for id in Set(recent.map(\.trackId)) where known[id] == nil {
            if let track = try? await musicProvider.getTrack(id: id) {
                known[id] = track
            }
        }
        let recentIds = Set(recent.map(\.trackId))
        return known.filter { recentIds.contains($0.key) || $0.key == current.id }
    }
}

private extension FeedState {
    func with(
        status: PlaybackStatus? = nil,
        lastIntentLabel: String? = nil,
        isCurrentTrackLiked: Bool? = nil
    ) -> FeedState {
        var copy = self
        if let status { copy.status = status }
        if let lastIntentLabel { copy.lastIntentLabel = lastIntentLabel }
        if let isCurrentTrackLiked { copy.isCurrentTrackLiked = isCurrentTrackLiked }
        return copy
    }
}
