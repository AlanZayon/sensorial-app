import Foundation

public struct PreferenceUpdateConfig: Sendable {
    public var lowIntensityScale: Double = 0.35
    public var mediumIntensityScale: Double = 0.7
    public var highIntensityScale: Double = 1.0
    public var velocityBase: Double = 0.5
    public var velocityWeight: Double = 0.5
    public var targetListeningMs: Int64 = 20_000
    public var informedRejectionBonus: Double = 0.25
    public var positiveGenreDelta: Double = 0.25
    public var negativeGenreDelta: Double = 0.18
    public var positiveArtistDelta: Double = 0.2
    public var negativeArtistDelta: Double = 0.12
    public var featureBlend: Double = 0.15
    public var explorationStep: Double = 0.08
    public var similarityStep: Double = 0.08
    public var listeningGenreDelta: Double = 0.2
    public var listeningArtistDelta: Double = 0.15
    public var contextualGenreDelta: Double = 0.15
    public var quickSkipProgress: Double = 0.12
    public var quickSkipListenScale: Double = 0.9
    public var quickSkipProfileScale: Double = 0.4
    public var minBucketSamplesForContext: Int = 3

    public init() {}
}

public struct UserPreferenceProfile: Sendable, Equatable {
    public var genreScores: [GenreId: Double]
    public var artistScores: [ArtistId: Double]
    public var genreListeningScores: [GenreId: Double]
    public var artistListeningScores: [ArtistId: Double]
    public var timeBucketGenreScores: [Int: [GenreId: Double]]
    public var timeBucketSampleCounts: [Int: Int]
    public var weekendGenreScores: [GenreId: Double]
    public var weekdayGenreScores: [GenreId: Double]
    public var weekendSampleCount: Int
    public var weekdaySampleCount: Int
    public var preferredFeatures: AudioFeatures?
    public var explorationPreference: Double
    public var positiveSignalCount: Int
    public var negativeSignalCount: Int

    public init(
        genreScores: [GenreId: Double] = [:],
        artistScores: [ArtistId: Double] = [:],
        genreListeningScores: [GenreId: Double] = [:],
        artistListeningScores: [ArtistId: Double] = [:],
        timeBucketGenreScores: [Int: [GenreId: Double]] = [:],
        timeBucketSampleCounts: [Int: Int] = [:],
        weekendGenreScores: [GenreId: Double] = [:],
        weekdayGenreScores: [GenreId: Double] = [:],
        weekendSampleCount: Int = 0,
        weekdaySampleCount: Int = 0,
        preferredFeatures: AudioFeatures? = nil,
        explorationPreference: Double = 0.5,
        positiveSignalCount: Int = 0,
        negativeSignalCount: Int = 0
    ) {
        self.genreScores = genreScores
        self.artistScores = artistScores
        self.genreListeningScores = genreListeningScores
        self.artistListeningScores = artistListeningScores
        self.timeBucketGenreScores = timeBucketGenreScores
        self.timeBucketSampleCounts = timeBucketSampleCounts
        self.weekendGenreScores = weekendGenreScores
        self.weekdayGenreScores = weekdayGenreScores
        self.weekendSampleCount = weekendSampleCount
        self.weekdaySampleCount = weekdaySampleCount
        self.preferredFeatures = preferredFeatures
        self.explorationPreference = explorationPreference
        self.positiveSignalCount = positiveSignalCount
        self.negativeSignalCount = negativeSignalCount
    }

    public func genreScore(_ id: GenreId) -> Double { genreScores[id] ?? 0 }
    public func artistScore(_ id: ArtistId) -> Double { artistScores[id] ?? 0 }
    public func genreListeningScore(_ id: GenreId) -> Double { genreListeningScores[id] ?? 0 }
    public func artistListeningScore(_ id: ArtistId) -> Double { artistListeningScores[id] ?? 0 }

    public func contextualGenreScore(
        _ id: GenreId,
        hour: Int,
        dayOfWeek: Int = 1,
        config: PreferenceUpdateConfig = PreferenceUpdateConfig()
    ) -> Double {
        let bucket = timeBucketForHour(hour)
        let samples = timeBucketSampleCounts[bucket] ?? 0
        let hourScore: Double
        if samples < config.minBucketSamplesForContext {
            hourScore = genreScore(id)
        } else {
            hourScore = timeBucketGenreScores[bucket]?[id] ?? genreScore(id)
        }
        let isWeekend = dayOfWeek >= 6
        let dowSamples = isWeekend ? weekendSampleCount : weekdaySampleCount
        if dowSamples < config.minBucketSamplesForContext { return hourScore }
        let dowScore = isWeekend
            ? (weekendGenreScores[id] ?? hourScore)
            : (weekdayGenreScores[id] ?? hourScore)
        return 0.65 * hourScore + 0.35 * dowScore
    }

    /// Top positive genres for provider seeding (highest scores first).
    public func topPositiveGenres(limit: Int = 3) -> [GenreId] {
        var combined: [GenreId: Double] = genreScores
        for (key, value) in genreListeningScores {
            combined[key, default: 0] += value * 0.8
        }
        return combined
            .filter { $0.value > 0 }
            .sorted { $0.value > $1.value }
            .prefix(limit)
            .map(\.key)
    }

    public func topPositiveArtists(limit: Int = 5) -> [ArtistId] {
        var combined: [ArtistId: Double] = artistScores
        for (key, value) in artistListeningScores {
            combined[key, default: 0] += value * 0.8
        }
        return combined
            .filter { $0.value > 0 }
            .sorted { $0.value > $1.value }
            .prefix(limit)
            .map(\.key)
    }

    public func applying(
        track: Track,
        interaction: TrackInteraction,
        config: PreferenceUpdateConfig = PreferenceUpdateConfig()
    ) -> UserPreferenceProfile {
        switch interaction.interactionType {
        case .explicitLike, .explicitDislike, .explicitUnlike:
            return applyingExplicit(track: track, interaction: interaction, config: config)
        case .discovery:
            return applyingDiscovery(track: track, interaction: interaction, config: config)
                .applyingListening(track: track, interaction: interaction, config: config)
        case .navigationAdvance, .navigationReturn:
            return applyingListening(track: track, interaction: interaction, config: config)
        case .preference:
            return applyingLegacy(track: track, interaction: interaction, config: config)
        }
    }

    private func applyingExplicit(
        track: Track,
        interaction: TrackInteraction,
        config: PreferenceUpdateConfig
    ) -> UserPreferenceProfile {
        let scale = signalScale(interaction, config: config)
        var copy = self
        switch interaction.interactionType {
        case .explicitLike:
            copy.positiveSignalCount += 1
            copy.genreScores = bump(copy.genreScores, keys: track.genres, delta: config.positiveGenreDelta * scale)
            copy.artistScores = bump(copy.artistScores, keys: track.artists.map(\.id), delta: config.positiveArtistDelta * scale)
            copy.preferredFeatures = blend(copy.preferredFeatures, track.audioFeatures, amount: config.featureBlend * scale)
        case .explicitUnlike:
            copy.positiveSignalCount = max(0, copy.positiveSignalCount - 1)
            copy.genreScores = bump(copy.genreScores, keys: track.genres, delta: -config.positiveGenreDelta * scale)
            copy.artistScores = bump(copy.artistScores, keys: track.artists.map(\.id), delta: -config.positiveArtistDelta * scale)
        case .explicitDislike:
            copy.negativeSignalCount += 1
            let rejectionScale = scale * informedRejectionMultiplier(interaction, config: config)
            copy.genreScores = bump(copy.genreScores, keys: track.genres, delta: -config.negativeGenreDelta * rejectionScale)
            copy.artistScores = bump(copy.artistScores, keys: track.artists.map(\.id), delta: -config.negativeArtistDelta * rejectionScale)
        default:
            break
        }
        return copy
    }

    private func applyingListening(
        track: Track,
        interaction: TrackInteraction,
        config: PreferenceUpdateConfig
    ) -> UserPreferenceProfile {
        var copy = self
        let progress = interaction.effectiveListeningProgress
        let isQuickSkip = progress <= config.quickSkipProgress &&
            interaction.interactionType != .navigationReturn &&
            (interaction.interactionType == .navigationAdvance || interaction.interactionType == .discovery)

        if isQuickSkip {
            copy.genreListeningScores = bump(
                copy.genreListeningScores,
                keys: track.genres,
                delta: -config.listeningGenreDelta * config.quickSkipListenScale
            )
            copy.artistListeningScores = bump(
                copy.artistListeningScores,
                keys: track.artists.map(\.id),
                delta: -config.listeningArtistDelta * config.quickSkipListenScale
            )
            copy.genreScores = bump(
                copy.genreScores,
                keys: track.genres,
                delta: -config.negativeGenreDelta * config.quickSkipProfileScale
            )
            copy.artistScores = bump(
                copy.artistScores,
                keys: track.artists.map(\.id),
                delta: -config.negativeArtistDelta * config.quickSkipProfileScale
            )
            return copy
        }

        let deltaScale = interaction.interactionType == .navigationReturn ? 0.85 : progress
        copy.genreListeningScores = bump(
            copy.genreListeningScores,
            keys: track.genres,
            delta: config.listeningGenreDelta * deltaScale
        )
        copy.artistListeningScores = bump(
            copy.artistListeningScores,
            keys: track.artists.map(\.id),
            delta: config.listeningArtistDelta * deltaScale
        )
        let bucket = timeBucketForHour(interaction.localHour)
        let bucketGenres = bump(
            copy.timeBucketGenreScores[bucket] ?? [:],
            keys: track.genres,
            delta: config.contextualGenreDelta * deltaScale
        )
        copy.timeBucketGenreScores[bucket] = bucketGenres
        copy.timeBucketSampleCounts[bucket, default: 0] += 1
        return copy
    }

    private func applyingDiscovery(
        track: Track,
        interaction: TrackInteraction,
        config: PreferenceUpdateConfig
    ) -> UserPreferenceProfile {
        let scale = signalScale(interaction, config: config) * 0.5
        var copy = self
        switch interaction.discoveryMode {
        case .explore:
            copy.explorationPreference = min(1, max(0, copy.explorationPreference + config.explorationStep * scale))
        case .deepen:
            copy.explorationPreference = min(1, max(0, copy.explorationPreference - config.similarityStep * scale))
            copy.preferredFeatures = blend(copy.preferredFeatures, track.audioFeatures, amount: config.featureBlend * scale * 0.4)
            copy.genreScores = bump(copy.genreScores, keys: track.genres, delta: config.positiveGenreDelta * scale * 0.25)
        case .normal:
            break
        }
        return copy
    }

    private func applyingLegacy(
        track: Track,
        interaction: TrackInteraction,
        config: PreferenceUpdateConfig
    ) -> UserPreferenceProfile {
        var mapped = interaction
        switch interaction.verticalIntent {
        case .positive:
            mapped = TrackInteraction(
                id: interaction.id,
                trackId: interaction.trackId,
                interactionType: .explicitLike,
                gestureIntensity: interaction.gestureIntensity,
                gestureVelocity: interaction.gestureVelocity,
                listeningDurationMs: interaction.listeningDurationMs,
                timestampEpochMs: interaction.timestampEpochMs,
                listeningProgress: interaction.listeningProgress,
                localHour: interaction.localHour,
                dayOfWeek: interaction.dayOfWeek
            )
        case .negative:
            mapped = TrackInteraction(
                id: interaction.id,
                trackId: interaction.trackId,
                interactionType: .explicitDislike,
                gestureIntensity: interaction.gestureIntensity,
                gestureVelocity: interaction.gestureVelocity,
                listeningDurationMs: interaction.listeningDurationMs,
                timestampEpochMs: interaction.timestampEpochMs,
                listeningProgress: interaction.listeningProgress,
                localHour: interaction.localHour,
                dayOfWeek: interaction.dayOfWeek
            )
        case .none:
            break
        }
        var result = applyingExplicit(track: track, interaction: mapped, config: config)
        let mode: DiscoveryMode
        switch interaction.horizontalIntent {
        case .similar: mode = .deepen
        case .explore: mode = .explore
        case .none: mode = .normal
        }
        let discovery = TrackInteraction(
            id: interaction.id,
            trackId: interaction.trackId,
            interactionType: .discovery,
            gestureIntensity: interaction.gestureIntensity,
            gestureVelocity: interaction.gestureVelocity,
            listeningDurationMs: interaction.listeningDurationMs,
            timestampEpochMs: interaction.timestampEpochMs,
            listeningProgress: interaction.listeningProgress,
            discoveryMode: mode,
            localHour: interaction.localHour,
            dayOfWeek: interaction.dayOfWeek
        )
        return result.applyingDiscovery(track: track, interaction: discovery, config: config)
            .applyingListening(track: track, interaction: interaction, config: config)
    }

    private func signalScale(_ interaction: TrackInteraction, config: PreferenceUpdateConfig) -> Double {
        let intensity: Double
        switch interaction.gestureIntensity {
        case .low: intensity = config.lowIntensityScale
        case .medium: intensity = config.mediumIntensityScale
        case .high: intensity = config.highIntensityScale
        }
        let velocity = config.velocityBase + config.velocityWeight * min(1, max(0, interaction.gestureVelocity))
        let listening = min(1, max(0, Double(interaction.listeningDurationMs) / Double(config.targetListeningMs)))
        let listeningFactor = 0.35 + 0.65 * listening
        return intensity * velocity * listeningFactor
    }

    private func informedRejectionMultiplier(_ interaction: TrackInteraction, config: PreferenceUpdateConfig) -> Double {
        let longListen = interaction.listeningDurationMs >= config.targetListeningMs
        let strong = interaction.gestureIntensity == .high
        return longListen && strong ? 1 + config.informedRejectionBonus : 1
    }

    private func bump<Key: Hashable>(_ map: [Key: Double], keys: [Key], delta: Double) -> [Key: Double] {
        guard !keys.isEmpty else { return map }
        var result = map
        for key in keys {
            result[key] = min(1, max(-1, (result[key] ?? 0) + delta))
        }
        return result
    }

    private func blend(_ current: AudioFeatures?, _ incoming: AudioFeatures, amount: Double) -> AudioFeatures {
        let t = min(1, max(0, amount))
        guard let current else { return incoming }
        func lerp(_ a: Double, _ b: Double) -> Double { a + (b - a) * t }
        return AudioFeatures(
            energy: lerp(current.energy, incoming.energy),
            danceability: lerp(current.danceability, incoming.danceability),
            valence: lerp(current.valence, incoming.valence),
            tempo: lerp(current.tempo, incoming.tempo)
        )
    }
}
