package com.sensorial.domain.preference

import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.GestureIntensity
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackInteraction
import com.sensorial.domain.model.timeBucketForHour

data class PreferenceUpdateConfig(
    val lowIntensityScale: Double = 0.35,
    val mediumIntensityScale: Double = 0.7,
    val highIntensityScale: Double = 1.0,
    val velocityBase: Double = 0.5,
    val velocityWeight: Double = 0.5,
    val targetListeningMs: Long = 20_000L,
    val informedRejectionBonus: Double = 0.25,
    val positiveGenreDelta: Double = 0.25,
    val negativeGenreDelta: Double = 0.18,
    val positiveArtistDelta: Double = 0.2,
    val negativeArtistDelta: Double = 0.12,
    val featureBlend: Double = 0.15,
    val explorationStep: Double = 0.08,
    val deepenStep: Double = 0.08,
    val listeningGenreDelta: Double = 0.2,
    val listeningArtistDelta: Double = 0.15,
    val contextualGenreDelta: Double = 0.15,
    /** Progress at/below this on advance/discovery counts as lost interest (quick skip). */
    val quickSkipProgress: Double = 0.12,
    /**
     * Minimum listen time used to raise the progress threshold on short tracks
     * (duration-aware quick-skip).
     */
    val quickSkipMinMs: Long = 8_000L,
    /** Strength of durable listening aversion on quick skip (vs long-listen positive). */
    val quickSkipListenScale: Double = 0.9,
    /** Soft durable profile aversion on quick skip (weaker than explicit dislike). */
    val quickSkipProfileScale: Double = 0.4,
    val minBucketSamplesForContext: Int = 3,
    val scoreClamp: ClosedFloatingPointRange<Double> = -1.0..1.0,
    val explorationClamp: ClosedFloatingPointRange<Double> = 0.0..1.0,
)

/**
 * Progressive preference model: explicit likes, listening behavior, and time context.
 */
data class UserPreferenceProfile(
    /** Explicit like/dislike affinity. */
    val genreScores: Map<GenreId, Double> = emptyMap(),
    val artistScores: Map<ArtistId, Double> = emptyMap(),
    /** Behavioral listening affinity (independent of explicit likes). */
    val genreListeningScores: Map<GenreId, Double> = emptyMap(),
    val artistListeningScores: Map<ArtistId, Double> = emptyMap(),
    /** Per time-bucket (0..5) genre affinities learned from real listening. */
    val timeBucketGenreScores: Map<Int, Map<GenreId, Double>> = emptyMap(),
    val timeBucketSampleCounts: Map<Int, Int> = emptyMap(),
    /** Weekend (ISO dow 6–7) vs weekday genre affinities. */
    val weekendGenreScores: Map<GenreId, Double> = emptyMap(),
    val weekdayGenreScores: Map<GenreId, Double> = emptyMap(),
    val weekendSampleCount: Int = 0,
    val weekdaySampleCount: Int = 0,
    val preferredFeatures: AudioFeatures? = null,
    /** 0 = prefer deepen/similar, 1 = prefer exploration */
    val explorationPreference: Double = 0.5,
    val positiveSignalCount: Int = 0,
    val negativeSignalCount: Int = 0,
) {
    fun genreScore(id: GenreId): Double = genreScores[id] ?: 0.0

    fun artistScore(id: ArtistId): Double = artistScores[id] ?: 0.0

    fun genreListeningScore(id: GenreId): Double = genreListeningScores[id] ?: 0.0

    fun artistListeningScore(id: ArtistId): Double = artistListeningScores[id] ?: 0.0

    fun contextualGenreScore(
        id: GenreId,
        hour: Int,
        dayOfWeek: Int = 1,
        config: PreferenceUpdateConfig = PreferenceUpdateConfig(),
    ): Double {
        val bucket = timeBucketForHour(hour)
        val samples = timeBucketSampleCounts[bucket] ?: 0
        val hourScore = if (samples < config.minBucketSamplesForContext) {
            genreScore(id)
        } else {
            timeBucketGenreScores[bucket]?.get(id) ?: genreScore(id)
        }
        val isWeekend = dayOfWeek >= 6
        val dowSamples = if (isWeekend) weekendSampleCount else weekdaySampleCount
        if (dowSamples < config.minBucketSamplesForContext) return hourScore
        val dowScore = if (isWeekend) {
            weekendGenreScores[id] ?: hourScore
        } else {
            weekdayGenreScores[id] ?: hourScore
        }
        return 0.65 * hourScore + 0.35 * dowScore
    }

    fun topPositiveGenres(limit: Int = 3): List<GenreId> {
        val combined = linkedMapOf<GenreId, Double>()
        genreScores.forEach { (k, v) -> combined[k] = v }
        genreListeningScores.forEach { (k, v) -> combined[k] = (combined[k] ?: 0.0) + v * 0.8 }
        return combined.entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    fun topPositiveArtists(limit: Int = 5): List<ArtistId> {
        val combined = linkedMapOf<ArtistId, Double>()
        artistScores.forEach { (k, v) -> combined[k] = v }
        artistListeningScores.forEach { (k, v) -> combined[k] = (combined[k] ?: 0.0) + v * 0.8 }
        return combined.entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    /**
     * Routes an interaction to the appropriate profile update path.
     * Navigation alone never applies as an explicit like/dislike.
     */
    fun applying(
        track: Track,
        interaction: TrackInteraction,
        config: PreferenceUpdateConfig = PreferenceUpdateConfig(),
    ): UserPreferenceProfile = when (interaction.interactionType) {
        InteractionType.ExplicitLike,
        InteractionType.ExplicitDislike,
        InteractionType.ExplicitUnlike,
        -> applyingExplicit(track, interaction, config)
        InteractionType.Discovery -> applyingDiscovery(track, interaction, config)
            .applyingListening(track, interaction, config)
        InteractionType.NavigationAdvance,
        InteractionType.NavigationReturn,
        -> applyingListening(track, interaction, config)
        InteractionType.Preference -> applyingLegacyPreference(track, interaction, config)
    }

    fun applyingExplicit(
        track: Track,
        interaction: TrackInteraction,
        config: PreferenceUpdateConfig = PreferenceUpdateConfig(),
    ): UserPreferenceProfile {
        val scale = signalScale(interaction, config)
        var genres = genreScores
        var artists = artistScores
        var features = preferredFeatures
        var positives = positiveSignalCount
        var negatives = negativeSignalCount

        when (interaction.interactionType) {
            InteractionType.ExplicitLike -> {
                positives += 1
                genres = bump(genres, track.genres, config.positiveGenreDelta * scale, config.scoreClamp)
                artists = bump(artists, track.artists.map { it.id }, config.positiveArtistDelta * scale, config.scoreClamp)
                features = blendFeatures(features, track.audioFeatures, config.featureBlend * scale)
            }
            InteractionType.ExplicitUnlike -> {
                // Reverse an accidental like with matching deltas.
                positives = (positives - 1).coerceAtLeast(0)
                genres = bump(genres, track.genres, -config.positiveGenreDelta * scale, config.scoreClamp)
                artists = bump(
                    artists,
                    track.artists.map { it.id },
                    -config.positiveArtistDelta * scale,
                    config.scoreClamp,
                )
            }
            InteractionType.ExplicitDislike -> {
                negatives += 1
                val rejection = scale * informedRejectionMultiplier(interaction, config)
                // Soft dislike: reduce relevance, do not ban artist/genre.
                genres = bump(genres, track.genres, -config.negativeGenreDelta * rejection, config.scoreClamp)
                artists = bump(
                    artists,
                    track.artists.map { it.id },
                    -config.negativeArtistDelta * rejection,
                    config.scoreClamp,
                )
            }
            else -> return this
        }

        return copy(
            genreScores = genres,
            artistScores = artists,
            preferredFeatures = features,
            positiveSignalCount = positives,
            negativeSignalCount = negatives,
        )
    }

    fun applyingListening(
        track: Track,
        interaction: TrackInteraction,
        config: PreferenceUpdateConfig = PreferenceUpdateConfig(),
    ): UserPreferenceProfile {
        val progress = interaction.effectiveListeningProgress
        val isQuickSkip = QuickSkip.isQuickSkip(interaction, track.durationMs, config)

        // Quick skip = lost interest: durable aversion that survives explorationBoost decay.
        if (isQuickSkip) {
            val listenGenres = bump(
                genreListeningScores,
                track.genres,
                -config.listeningGenreDelta * config.quickSkipListenScale,
                config.scoreClamp,
            )
            val listenArtists = bump(
                artistListeningScores,
                track.artists.map { it.id },
                -config.listeningArtistDelta * config.quickSkipListenScale,
                config.scoreClamp,
            )
            val genres = bump(
                genreScores,
                track.genres,
                -config.negativeGenreDelta * config.quickSkipProfileScale,
                config.scoreClamp,
            )
            val artists = bump(
                artistScores,
                track.artists.map { it.id },
                -config.negativeArtistDelta * config.quickSkipProfileScale,
                config.scoreClamp,
            )
            return copy(
                genreListeningScores = listenGenres,
                artistListeningScores = listenArtists,
                genreScores = genres,
                artistScores = artists,
            )
        }

        val deltaScale = when {
            interaction.interactionType == InteractionType.NavigationReturn -> 0.85
            else -> progress
        }
        val listenGenres = bump(
            genreListeningScores,
            track.genres,
            config.listeningGenreDelta * deltaScale,
            config.scoreClamp,
        )
        val listenArtists = bump(
            artistListeningScores,
            track.artists.map { it.id },
            config.listeningArtistDelta * deltaScale,
            config.scoreClamp,
        )

        val bucket = timeBucketForHour(interaction.localHour)
        val bucketGenres = (timeBucketGenreScores[bucket] ?: emptyMap()).let { current ->
            bump(current, track.genres, config.contextualGenreDelta * deltaScale, config.scoreClamp)
        }
        val counts = timeBucketSampleCounts.toMutableMap()
        counts[bucket] = (counts[bucket] ?: 0) + 1

        val isWeekend = interaction.dayOfWeek >= 6
        val dowGenres = if (isWeekend) {
            bump(weekendGenreScores, track.genres, config.contextualGenreDelta * deltaScale, config.scoreClamp)
        } else {
            bump(weekdayGenreScores, track.genres, config.contextualGenreDelta * deltaScale, config.scoreClamp)
        }

        return copy(
            genreListeningScores = listenGenres,
            artistListeningScores = listenArtists,
            timeBucketGenreScores = timeBucketGenreScores + (bucket to bucketGenres),
            timeBucketSampleCounts = counts,
            weekendGenreScores = if (isWeekend) dowGenres else weekendGenreScores,
            weekdayGenreScores = if (!isWeekend) dowGenres else weekdayGenreScores,
            weekendSampleCount = if (isWeekend) weekendSampleCount + 1 else weekendSampleCount,
            weekdaySampleCount = if (!isWeekend) weekdaySampleCount + 1 else weekdaySampleCount,
            preferredFeatures = blendFeatures(
                preferredFeatures,
                track.audioFeatures,
                config.featureBlend * deltaScale * 0.35,
            ),
        )
    }

    fun applyingDiscovery(
        track: Track,
        interaction: TrackInteraction,
        config: PreferenceUpdateConfig = PreferenceUpdateConfig(),
    ): UserPreferenceProfile {
        val scale = signalScale(interaction, config) * 0.5
        var exploration = explorationPreference
        var features = preferredFeatures
        var genres = genreScores
        when (interaction.discoveryMode) {
            DiscoveryMode.Explore -> {
                exploration = (exploration + config.explorationStep * scale).coerceIn(config.explorationClamp)
            }
            DiscoveryMode.Deepen -> {
                exploration = (exploration - config.deepenStep * scale).coerceIn(config.explorationClamp)
                features = blendFeatures(features, track.audioFeatures, config.featureBlend * scale * 0.4)
                genres = bump(genres, track.genres, config.positiveGenreDelta * scale * 0.25, config.scoreClamp)
            }
            DiscoveryMode.Normal -> Unit
        }
        return copy(
            explorationPreference = exploration,
            preferredFeatures = features,
            genreScores = genres,
        )
    }

    /** Backward-compatible path for old Preference interactions. */
    private fun applyingLegacyPreference(
        track: Track,
        interaction: TrackInteraction,
        config: PreferenceUpdateConfig,
    ): UserPreferenceProfile {
        val asExplicit = when (interaction.verticalIntent) {
            com.sensorial.domain.model.VerticalIntent.Positive ->
                copy().applyingExplicit(
                    track,
                    interaction.copy(interactionType = InteractionType.ExplicitLike),
                    config,
                )
            com.sensorial.domain.model.VerticalIntent.Negative ->
                copy().applyingExplicit(
                    track,
                    interaction.copy(interactionType = InteractionType.ExplicitDislike),
                    config,
                )
            com.sensorial.domain.model.VerticalIntent.None -> this
        }
        val mode = when (interaction.horizontalIntent) {
            com.sensorial.domain.model.HorizontalIntent.Similar -> DiscoveryMode.Deepen
            com.sensorial.domain.model.HorizontalIntent.Explore -> DiscoveryMode.Explore
            com.sensorial.domain.model.HorizontalIntent.None -> DiscoveryMode.Normal
        }
        return asExplicit.applyingDiscovery(
            track,
            interaction.copy(interactionType = InteractionType.Discovery, discoveryMode = mode),
            config,
        ).applyingListening(track, interaction, config)
    }

    private fun signalScale(interaction: TrackInteraction, config: PreferenceUpdateConfig): Double {
        val intensity = when (interaction.gestureIntensity) {
            GestureIntensity.Low -> config.lowIntensityScale
            GestureIntensity.Medium -> config.mediumIntensityScale
            GestureIntensity.High -> config.highIntensityScale
        }
        val velocity = config.velocityBase + config.velocityWeight * interaction.gestureVelocity.coerceIn(0.0, 1.0)
        val listening = interaction.effectiveListeningProgress.takeIf { it > 0 }
            ?: (interaction.listeningDurationMs.toDouble() / config.targetListeningMs.toDouble()).coerceIn(0.0, 1.0)
        val listeningFactor = 0.35 + 0.65 * listening
        return intensity * velocity * listeningFactor
    }

    private fun informedRejectionMultiplier(
        interaction: TrackInteraction,
        config: PreferenceUpdateConfig,
    ): Double {
        val longListen = interaction.effectiveListeningProgress >= 0.7 ||
            interaction.listeningDurationMs >= config.targetListeningMs
        val strong = interaction.gestureIntensity == GestureIntensity.High
        return if (longListen && strong) 1.0 + config.informedRejectionBonus else 1.0
    }

    private fun <K> bump(
        current: Map<K, Double>,
        keys: List<K>,
        delta: Double,
        clamp: ClosedFloatingPointRange<Double>,
    ): Map<K, Double> {
        if (keys.isEmpty() || delta == 0.0) return current
        val mutable = current.toMutableMap()
        keys.forEach { key ->
            mutable[key] = ((mutable[key] ?: 0.0) + delta).coerceIn(clamp)
        }
        return mutable
    }

    private fun blendFeatures(
        current: AudioFeatures?,
        incoming: AudioFeatures,
        blend: Double,
    ): AudioFeatures {
        val amount = blend.coerceIn(0.0, 1.0)
        if (current == null) return incoming
        return AudioFeatures(
            energy = lerp(current.energy, incoming.energy, amount),
            danceability = lerp(current.danceability, incoming.danceability, amount),
            valence = lerp(current.valence, incoming.valence, amount),
            tempo = lerp(current.tempo, incoming.tempo, amount),
        )
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}
