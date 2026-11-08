package com.sensorial.domain.recommendation

import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.GestureIntensity
import com.sensorial.domain.model.HorizontalIntent
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction
import com.sensorial.domain.model.VerticalIntent
import com.sensorial.domain.preference.PreferenceUpdateConfig
import com.sensorial.domain.preference.UserPreferenceProfile
import kotlin.math.abs
import kotlin.math.min

data class RecommendationWeights(
    val genre: Double = 1.0,
    val artist: Double = 0.8,
    val features: Double = 0.6,
    val recentSignals: Double = 0.5,
    val exploration: Double = 0.7,
    /** @deprecated Global session bias removed; kept for serialization compatibility (ignored in score). */
    val listening: Double = 0.0,
    val listeningBehavior: Double = 0.55,
    val contextualTime: Double = 0.35,
    val similarity: Double = 0.5,
    val artistDiversity: Double = 0.7,
    val trackRepetition: Double = 0.85,
    val artistFocusRelief: Double = 1.0,
    val negativePenalty: Double = 1.0,
    val coldStart: Double = 0.08,
    val sessionAffinity: Double = 0.45,
    val artistGraph: Double = 0.5,
    val popularityFit: Double = 0.35,
    val eraFit: Double = 0.25,
    val localSimilarity: Double = 0.4,
) {
    init {
        require(
            listOf(
                genre, artist, features, recentSignals, exploration, listening,
                listeningBehavior, contextualTime, similarity, artistDiversity,
                trackRepetition, artistFocusRelief, negativePenalty, coldStart,
                sessionAffinity, artistGraph, popularityFit, eraFit, localSimilarity,
            ).all { it >= 0.0 },
        ) { "weights must be non-negative" }
    }
}

data class SignalScoringConfig(
    val recentWindow: Int = 8,
    val targetListeningMs: Long = 20_000L,
    val intensityLow: Double = 0.35,
    val intensityMedium: Double = 0.7,
    val intensityHigh: Double = 1.0,
    val velocityBase: Double = 0.5,
    val velocityWeight: Double = 0.5,
    val positiveRecentCoeff: Double = 0.35,
    val negativeRecentCoeff: Double = 0.4,
    val similarProximityCoeff: Double = 0.3,
    val similarNoveltyPenalty: Double = 0.15,
    val exploreNoveltyCoeff: Double = 0.35,
    val exploreProximityPenalty: Double = 0.2,
    val coldStartCenterEnergy: Double = 0.5,
    val coldStartCenterDance: Double = 0.5,
    val deepenSimilarityBoost: Double = 0.45,
    val exploreNoveltyBoost: Double = 0.45,
)

data class RecommendationContext(
    val currentTrack: Track?,
    val recentInteractions: List<TrackInteraction>,
    val excludedIds: Set<TrackId>,
    val interactionTracks: Map<TrackId, Track> = emptyMap(),
    val seedGenres: List<GenreId> = emptyList(),
    val topArtists: List<ArtistId> = emptyList(),
    val relatedArtistIds: Set<ArtistId> = emptySet(),
    val discoveryMode: DiscoveryMode = DiscoveryMode.Normal,
    val recentTracks: List<Track> = emptyList(),
    val localHour: Int = 12,
    val dayOfWeek: Int = 1,
    val sessionAffinity: SessionAffinity = SessionAffinity.EMPTY,
    /** Background warm uses a richer pool; swipe miss uses a faster interactive path. */
    val prefetch: Boolean = false,
    /** Hard-block fingerprints (liked / well-heard works, including remasters). */
    val blockedFingerprints: Set<String> = emptySet(),
)

data class ScoreBreakdown(
    val genre: Double,
    val artist: Double,
    val features: Double,
    val recent: Double,
    val exploration: Double,
    val listening: Double,
    val listeningBehavior: Double,
    val contextualTime: Double,
    val similarity: Double,
    val session: Double,
    val artistGraph: Double,
    val popularityFit: Double,
    val eraFit: Double,
    val localSimilarity: Double,
    val artistDiversity: Double,
    val trackRepetition: Double,
    val negative: Double,
    val coldStart: Double,
    val total: Double,
)

data class ScoredTrack(
    val track: Track,
    val score: Double,
    val breakdown: ScoreBreakdown,
)

fun interface RecommendationEngine {
    fun recommend(
        userProfile: UserPreferenceProfile,
        context: RecommendationContext,
        candidateTracks: List<Track>,
    ): Track?
}

/**
 * Explainable linear scoring engine. Weights and signal coeffs live in config types.
 */
class ScoredHeuristicRecommendationEngine(
    private val weights: RecommendationWeights = RecommendationWeights(),
    private val signals: SignalScoringConfig = SignalScoringConfig(),
    private val diversity: DiversityConfig = DiversityConfig(),
    private val preferenceConfig: PreferenceUpdateConfig = PreferenceUpdateConfig(),
    private val trackSimilarity: TrackSimilarity = LocalFeatureTrackSimilarity(),
    /** When true, ranking uses [RecommendationWeightsPresets] for the active discovery mode. */
    private val useModePresets: Boolean = false,
) : RecommendationEngine {

    override fun recommend(
        userProfile: UserPreferenceProfile,
        context: RecommendationContext,
        candidateTracks: List<Track>,
    ): Track? = rankDetailed(userProfile, context, candidateTracks).firstOrNull()?.track

    fun rank(
        userProfile: UserPreferenceProfile,
        context: RecommendationContext,
        candidateTracks: List<Track>,
    ): List<ScoredTrack> = rankDetailed(userProfile, context, candidateTracks)

    fun rankDetailed(
        userProfile: UserPreferenceProfile,
        context: RecommendationContext,
        candidateTracks: List<Track>,
    ): List<ScoredTrack> {
        val recentForDiversity = context.recentTracks.ifEmpty {
            context.recentInteractions.mapNotNull { context.interactionTracks[it.trackId] }
        }
        val eligible = candidateTracks.filterNot { candidate ->
            candidate.id in context.excludedIds ||
                TrackFingerprint.of(candidate) in context.blockedFingerprints ||
                RecentVersionBlock.isBlocked(
                    candidate = candidate,
                    recentTracks = recentForDiversity,
                    currentTrack = context.currentTrack,
                    config = diversity,
                )
        }
        if (eligible.isEmpty()) return emptyList()
        val focus = ArtistFocusDetector.detect(context.recentInteractions, context.interactionTracks, diversity)
        // Deepen already pulls toward the current climate; focus relief would trap on one artist.
        val focusedArtistId = if (context.discoveryMode == DiscoveryMode.Deepen) {
            null
        } else {
            focus.focusedArtistId
        }
        val activeWeights = if (useModePresets) {
            RecommendationWeightsPresets.forMode(context.discoveryMode)
        } else {
            weights
        }
        return eligible
            .map { track -> scoreDetailed(track, userProfile, context, focusedArtistId, activeWeights) }
            .sortedByDescending { it.score }
    }

    fun score(
        track: Track,
        profile: UserPreferenceProfile,
        context: RecommendationContext,
    ): Double = scoreDetailed(track, profile, context, null, weights).score

    fun scoreDetailed(
        track: Track,
        profile: UserPreferenceProfile,
        context: RecommendationContext,
        focusedArtistId: ArtistId? = null,
        activeWeights: RecommendationWeights = weights,
    ): ScoredTrack {
        val genreAffinity = track.genres.map { profile.genreScore(it) }.averageOrZero()
        val artistAffinity = track.artists.map { profile.artistScore(it.id) }.averageOrZero()
        val featureAffinity = featureAffinity(profile.preferredFeatures, track.audioFeatures) *
            track.featureConfidence.coerceIn(0.25, 1.0)
        val recent = recentGestureSignal(track, context)
        val exploration = explorationAlignment(track, context, profile.explorationPreference) +
            context.sessionAffinity.explorationBoost * 0.5
        val listeningBehavior = track.genres.map { profile.genreListeningScore(it) }.averageOrZero() +
            track.artists.map { profile.artistListeningScore(it.id) }.averageOrZero() * 0.5
        val contextual = track.genres
            .map {
                profile.contextualGenreScore(
                    it,
                    context.localHour,
                    context.dayOfWeek,
                    preferenceConfig,
                )
            }
            .averageOrZero()
        val similarity = currentSimilarity(track, context.currentTrack)
        val session = track.genres.map { context.sessionAffinity.genreBoost(it) }.averageOrZero() +
            track.artists.map { context.sessionAffinity.artistBoost(it.id) }.averageOrZero() * 0.7
        val recentTracks = context.recentTracks.ifEmpty {
            context.recentInteractions.mapNotNull { context.interactionTracks[it.trackId] }
        }
        val currentPrimary = context.currentTrack?.primaryArtist?.id
        val sameArtistStreak = SameArtistStreak.count(
            primaryArtistId = currentPrimary,
            recentTracks = recentTracks,
            currentTrack = context.currentTrack,
        )
        val sharesCurrentArtist = currentPrimary != null &&
            track.artists.any { it.id == currentPrimary }
        val isRelatedArtist = track.artists.any { it.id in context.relatedArtistIds }
        val graph = artistGraphProximity(track, context, sameArtistStreak)
        val popularity = popularityFit(track, context.discoveryMode)
        val era = eraFit(track, recentTracks)
        val localSimRaw = context.currentTrack?.let {
            trackSimilarity.similarity(track, it, emptyList()) *
                track.featureConfidence.coerceIn(0.25, 1.0)
        } ?: 0.0
        val localSim = when (context.discoveryMode) {
            DiscoveryMode.Explore -> localSimRaw * 0.2
            DiscoveryMode.Deepen -> localSimRaw
            DiscoveryMode.Normal -> localSimRaw * 0.65
        }
        val artistPenalty = RecentArtistPenalty.score(
            candidate = track,
            recentTracks = recentTracks,
            config = diversity,
            focusedArtistId = focusedArtistId,
        )
        val trackPenalty = RecentTrackPenalty.score(
            candidate = track,
            recentTracks = recentTracks,
            config = diversity,
        )
        val negative = negativePressure(genreAffinity, artistAffinity)
        val cold = if (profile.isColdStart()) coldStartTieBreak(track) else 0.0

        val deepenExtra = context.sessionAffinity.deepenStreak.coerceAtMost(3) * 0.08
        val exploreExtra = context.sessionAffinity.exploreStreak.coerceAtMost(3) * 0.08
        // After repeating the same artist, soften Deepen's similarity pull so related can win.
        val deepenSimilarityScale = when {
            context.discoveryMode != DiscoveryMode.Deepen -> 1.0
            sameArtistStreak >= 3 && sharesCurrentArtist -> 0.35
            sameArtistStreak >= 2 && sharesCurrentArtist -> 0.55
            else -> 1.0
        }
        val modeSimilarityBoost = when (context.discoveryMode) {
            DiscoveryMode.Deepen ->
                (signals.deepenSimilarityBoost + deepenExtra) * similarity * deepenSimilarityScale
            DiscoveryMode.Explore -> -signals.exploreNoveltyBoost * similarity * 0.35
            DiscoveryMode.Normal -> 0.0
        }
        val modeExplorationBoost = when (context.discoveryMode) {
            DiscoveryMode.Explore -> (signals.exploreNoveltyBoost + exploreExtra) * (1.0 - similarity)
            DiscoveryMode.Deepen -> if (sameArtistStreak >= 2) {
                0.12 * (1.0 - similarity)
            } else {
                -0.15 * (1.0 - similarity)
            }
            DiscoveryMode.Normal -> context.sessionAffinity.explorationBoost * (1.0 - similarity) * 0.4
        }
        val deepenSameArtistPenalty = if (
            context.discoveryMode == DiscoveryMode.Deepen &&
            sharesCurrentArtist &&
            sameArtistStreak >= 2
        ) {
            (sameArtistStreak - 1) * 0.7
        } else {
            0.0
        }
        val deepenRelatedBoost = if (
            context.discoveryMode == DiscoveryMode.Deepen &&
            isRelatedArtist &&
            !sharesCurrentArtist &&
            sameArtistStreak >= 2
        ) {
            0.45 + (sameArtistStreak - 2).coerceAtMost(3) * 0.18
        } else {
            0.0
        }

        val genre = activeWeights.genre * genreAffinity
        val artist = activeWeights.artist * artistAffinity
        val features = activeWeights.features * featureAffinity
        val recentWeighted = activeWeights.recentSignals * recent
        val explorationWeighted = activeWeights.exploration * (exploration + modeExplorationBoost)
        val listeningBehaviorWeighted = activeWeights.listeningBehavior * listeningBehavior
        val contextualWeighted = activeWeights.contextualTime * contextual
        val similarityWeighted = activeWeights.similarity * (similarity + modeSimilarityBoost)
        val sessionWeighted = activeWeights.sessionAffinity * session
        val graphWeighted = activeWeights.artistGraph * graph
        val popularityWeighted = activeWeights.popularityFit * popularity
        val eraWeighted = activeWeights.eraFit * era
        val localSimWeighted = activeWeights.localSimilarity * localSim
        val artistDivWeighted = activeWeights.artistDiversity * artistPenalty
        val trackRepWeighted = activeWeights.trackRepetition * trackPenalty
        val negativeWeighted = activeWeights.negativePenalty * negative
        val coldWeighted = activeWeights.coldStart * cold

        val total = genre + artist + features + recentWeighted + explorationWeighted +
            listeningBehaviorWeighted + contextualWeighted + similarityWeighted +
            sessionWeighted + graphWeighted + popularityWeighted + eraWeighted + localSimWeighted +
            deepenRelatedBoost -
            artistDivWeighted - trackRepWeighted - negativeWeighted + coldWeighted -
            deepenSameArtistPenalty

        return ScoredTrack(
            track = track,
            score = total,
            breakdown = ScoreBreakdown(
                genre = genre,
                artist = artist,
                features = features,
                recent = recentWeighted,
                exploration = explorationWeighted,
                listening = 0.0,
                listeningBehavior = listeningBehaviorWeighted,
                contextualTime = contextualWeighted,
                similarity = similarityWeighted,
                session = sessionWeighted,
                artistGraph = graphWeighted,
                popularityFit = popularityWeighted,
                eraFit = eraWeighted,
                localSimilarity = localSimWeighted,
                artistDiversity = -artistDivWeighted,
                trackRepetition = -trackRepWeighted,
                negative = -negativeWeighted,
                coldStart = coldWeighted,
                total = total,
            ),
        )
    }

    private fun artistGraphProximity(
        candidate: Track,
        context: RecommendationContext,
        sameArtistStreak: Int = 0,
    ): Double {
        val current = context.currentTrack ?: return 0.0
        val candidateArtists = candidate.artists.map { it.id }.toSet()
        if (candidateArtists.any { it in current.artists.map { a -> a.id } }) {
            return when (context.discoveryMode) {
                DiscoveryMode.Deepen -> when {
                    sameArtistStreak >= 3 -> 0.1
                    sameArtistStreak >= 2 -> 0.35
                    else -> 0.85
                }
                DiscoveryMode.Explore -> -0.25
                DiscoveryMode.Normal -> 0.15
            }
        }
        if (candidateArtists.any { it in context.relatedArtistIds }) {
            return when (context.discoveryMode) {
                DiscoveryMode.Deepen -> when {
                    sameArtistStreak >= 2 -> 0.95
                    else -> 0.7
                }
                DiscoveryMode.Explore -> 0.2
                DiscoveryMode.Normal -> 0.45
            }
        }
        if (candidateArtists.any { it in context.topArtists }) {
            return when (context.discoveryMode) {
                DiscoveryMode.Explore -> 0.15
                else -> 0.4
            }
        }
        return when (context.discoveryMode) {
            DiscoveryMode.Explore -> 0.35
            else -> 0.0
        }
    }

    private fun popularityFit(track: Track, mode: DiscoveryMode): Double {
        val p = track.popularity.coerceIn(0, 100) / 100.0
        return when (mode) {
            DiscoveryMode.Deepen -> 0.35 + 0.65 * p
            DiscoveryMode.Explore -> 1.0 - abs(p - 0.4)
            DiscoveryMode.Normal -> 0.5 + 0.3 * p
        }
    }

    private fun eraFit(candidate: Track, recent: List<Track>): Double {
        val year = candidate.releaseYear ?: return 0.0
        val recentYears = recent.mapNotNull { it.releaseYear }
        if (recentYears.isEmpty()) return 0.0
        val mean = recentYears.average()
        val distance = abs(year - mean)
        return (1.0 - (distance / 25.0).coerceIn(0.0, 1.0))
    }

    private fun currentSimilarity(candidate: Track, current: Track?): Double {
        if (current == null) return 0.0
        return trackAffinity(candidate, current)
    }

    private fun recentGestureSignal(candidate: Track, context: RecommendationContext): Double {
        val recent = context.recentInteractions.takeLast(signals.recentWindow)
        if (recent.isEmpty()) return 0.0

        var score = 0.0
        recent.forEachIndexed { index, interaction ->
            val interacted = context.interactionTracks[interaction.trackId] ?: return@forEachIndexed
            val recency = (index + 1).toDouble() / recent.size
            val strength = signalStrength(interaction)
            val affinity = trackAffinity(candidate, interacted)
            val progress = interaction.effectiveListeningProgress

            when (interaction.interactionType) {
                InteractionType.ExplicitLike ->
                    score += signals.positiveRecentCoeff * affinity * strength * recency
                InteractionType.ExplicitUnlike ->
                    score -= signals.positiveRecentCoeff * affinity * strength * recency
                InteractionType.ExplicitDislike ->
                    score -= signals.negativeRecentCoeff * affinity * strength * recency
                InteractionType.NavigationAdvance, InteractionType.Discovery -> {
                    if (progress <= 0.12) {
                        // Quick skip: push away from similar tracks even after explorationBoost decays.
                        score -= signals.negativeRecentCoeff * affinity * 0.85 * recency
                    } else {
                        score += (progress - 0.45) * 0.25 * affinity * recency
                    }
                }
                InteractionType.NavigationReturn ->
                    score += 0.2 * affinity * recency
                InteractionType.Preference -> {
                    score += when (interaction.verticalIntent) {
                        VerticalIntent.Positive -> signals.positiveRecentCoeff * affinity * strength * recency
                        VerticalIntent.Negative -> -signals.negativeRecentCoeff * affinity * strength * recency
                        VerticalIntent.None -> 0.0
                    }
                }
            }

            val novelty = noveltyBetween(candidate, interacted)
            val proximity = 1.0 - novelty
            if (progress <= 0.12 &&
                interaction.interactionType in setOf(
                    InteractionType.NavigationAdvance,
                    InteractionType.Discovery,
                )
            ) {
                score -= signals.similarProximityCoeff * proximity * 0.9 * recency
            }
            val horizontal = when (interaction.discoveryMode) {
                DiscoveryMode.Deepen -> HorizontalIntent.Similar
                DiscoveryMode.Explore -> HorizontalIntent.Explore
                DiscoveryMode.Normal -> {
                    // Ignore legacy horizontal noise on Normal navigation events.
                    if (interaction.horizontalIntent != HorizontalIntent.None &&
                        interaction.interactionType == InteractionType.Preference
                    ) {
                        interaction.horizontalIntent
                    } else {
                        HorizontalIntent.None
                    }
                }
            }
            score += when (horizontal) {
                HorizontalIntent.Similar ->
                    (signals.similarProximityCoeff * proximity - signals.similarNoveltyPenalty * novelty) *
                        strength * recency
                HorizontalIntent.Explore ->
                    (signals.exploreNoveltyCoeff * novelty - signals.exploreProximityPenalty * proximity) *
                        strength * recency
                HorizontalIntent.None -> 0.0
            }
        }
        return score
    }

    private fun explorationAlignment(
        track: Track,
        context: RecommendationContext,
        explorationPreference: Double,
    ): Double {
        val current = context.currentTrack ?: return 0.0
        val noveltyVsCurrent = noveltyBetween(track, current)
        val recentGenres = context.recentInteractions
            .takeLast(signals.recentWindow)
            .mapNotNull { context.interactionTracks[it.trackId] }
            .flatMap { it.genres }
            .toSet()
        val genreDiversity = if (recentGenres.isEmpty()) {
            noveltyVsCurrent
        } else {
            val overlap = track.genres.count { it in recentGenres }.toDouble()
            1.0 / (1.0 + overlap)
        }
        val similarPull = (1.0 - noveltyVsCurrent) * featureAffinity(current.audioFeatures, track.audioFeatures)
        val explorePull = 0.6 * noveltyVsCurrent + 0.4 * genreDiversity
        val modeBias = when (context.discoveryMode) {
            DiscoveryMode.Deepen -> 0.2
            DiscoveryMode.Explore -> 0.8
            DiscoveryMode.Normal -> explorationPreference
        }
        return modeBias * explorePull + (1.0 - modeBias) * similarPull
    }

    private fun signalStrength(interaction: TrackInteraction): Double {
        val intensity = when (interaction.gestureIntensity) {
            GestureIntensity.Low -> signals.intensityLow
            GestureIntensity.Medium -> signals.intensityMedium
            GestureIntensity.High -> signals.intensityHigh
        }
        val velocity = signals.velocityBase + signals.velocityWeight * interaction.gestureVelocity
        val listening = interaction.effectiveListeningProgress.takeIf { it > 0 }
            ?: min(1.0, interaction.listeningDurationMs.toDouble() / signals.targetListeningMs.toDouble())
        return intensity * velocity * listening
    }

    private fun trackAffinity(a: Track, b: Track): Double {
        val sharedGenres = a.genres.intersect(b.genres.toSet()).size.toDouble()
        val sharedArtists = a.artists.map { it.id }.intersect(b.artists.map { it.id }.toSet()).size.toDouble()
        val genrePart = sharedGenres / maxOf(1, a.genres.size)
        val artistPart = if (sharedArtists > 0) 1.0 else 0.0
        val features = featureAffinity(a.audioFeatures, b.audioFeatures)
        return (0.45 * genrePart + 0.25 * artistPart + 0.3 * features).coerceIn(0.0, 1.0)
    }

    private fun noveltyBetween(a: Track, b: Track): Double {
        val sharedGenres = a.genres.intersect(b.genres.toSet()).size
        val sharedArtists = a.artists.map { it.id }.intersect(b.artists.map { it.id }.toSet()).size
        return 1.0 / (1.0 + sharedGenres + sharedArtists)
    }

    private fun featureAffinity(preferred: AudioFeatures?, candidate: AudioFeatures): Double {
        if (preferred == null) return 0.0
        val energy = 1.0 - abs(preferred.energy - candidate.energy)
        val dance = 1.0 - abs(preferred.danceability - candidate.danceability)
        val valence = 1.0 - abs(preferred.valence - candidate.valence)
        val tempo = 1.0 - (abs(preferred.tempo - candidate.tempo) / 80.0).coerceIn(0.0, 1.0)
        return (energy + dance + valence + tempo) / 4.0
    }

    private fun negativePressure(genreScore: Double, artistScore: Double): Double {
        val genreNeg = if (genreScore < 0) -genreScore else 0.0
        val artistNeg = if (artistScore < 0) -artistScore else 0.0
        return genreNeg + artistNeg
    }

    private fun coldStartTieBreak(track: Track): Double {
        val idHash = (track.id.value.hashCode().toLong() and 0x7fffffffL) / Int.MAX_VALUE.toDouble()
        val center =
            1.0 - (
                abs(track.audioFeatures.energy - signals.coldStartCenterEnergy) +
                    abs(track.audioFeatures.danceability - signals.coldStartCenterDance)
                ) / 2.0
        return 0.55 * center + 0.45 * idHash
    }

    private fun UserPreferenceProfile.isColdStart(): Boolean =
        positiveSignalCount + negativeSignalCount == 0 &&
            genreScores.isEmpty() &&
            artistScores.isEmpty() &&
            genreListeningScores.isEmpty()

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}
