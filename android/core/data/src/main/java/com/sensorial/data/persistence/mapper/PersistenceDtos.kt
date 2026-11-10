package com.sensorial.data.persistence.mapper

import com.sensorial.domain.history.HistoryConfig
import com.sensorial.domain.history.HistoryEntry
import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.GestureIntensity
import com.sensorial.domain.model.HorizontalIntent
import com.sensorial.domain.model.InteractionId
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.NavigationAction
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction
import com.sensorial.domain.model.VerticalIntent
import com.sensorial.domain.preference.UserPreferenceProfile
import com.sensorial.domain.recommendation.BanditArmStats
import com.sensorial.domain.recommendation.ContextualWeightPolicyState
import com.sensorial.domain.recommendation.RecommendationWeights
import com.sensorial.domain.recommendation.WeightArm
import com.sensorial.domain.repository.AppSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val PersistenceJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class TrackDto(
    val id: String,
    val title: String,
    val artists: List<ArtistDto>,
    val album: AlbumDto,
    val genres: List<String>,
    val audioFeatures: AudioFeaturesDto,
    val artworkUrl: String? = null,
    val durationMs: Long = 180_000L,
    val previewUrl: String? = null,
    val popularity: Int = 0,
    val featureConfidence: Double = 1.0,
    val explicit: Boolean = false,
)

@Serializable
data class HistoryEntryDto(
    val track: TrackDto,
    val discoveryMode: String = DiscoveryMode.Normal.name,
    val recommendedAtEpochMs: Long = 0L,
    val listeningProgress: Double? = null,
)

@Serializable
data class ArtistDto(val id: String, val name: String)

@Serializable
data class AlbumDto(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val releaseYear: Int? = null,
)

@Serializable
data class AudioFeaturesDto(
    val energy: Double,
    val danceability: Double,
    val valence: Double,
    val tempo: Double,
)

@Serializable
data class PreferenceProfileDto(
    val genreScores: Map<String, Double> = emptyMap(),
    val artistScores: Map<String, Double> = emptyMap(),
    val genreListeningScores: Map<String, Double> = emptyMap(),
    val artistListeningScores: Map<String, Double> = emptyMap(),
    val timeBucketGenreScores: Map<String, Map<String, Double>> = emptyMap(),
    val timeBucketSampleCounts: Map<String, Int> = emptyMap(),
    val weekendGenreScores: Map<String, Double> = emptyMap(),
    val weekdayGenreScores: Map<String, Double> = emptyMap(),
    val weekendSampleCount: Int = 0,
    val weekdaySampleCount: Int = 0,
    val preferredFeatures: AudioFeaturesDto? = null,
    val explorationPreference: Double = 0.5,
    val positiveSignalCount: Int = 0,
    val negativeSignalCount: Int = 0,
)

@Serializable
data class AppSettingsDto(
    val historyMaxSize: Int = HistoryConfig.DEFAULT_MAX_SIZE,
    val weights: RecommendationWeightsDto = RecommendationWeightsDto(),
    val bandit: BanditStateDto = BanditStateDto(),
)

@Serializable
data class BanditArmStatsDto(
    val pulls: Int = 0,
    val totalReward: Double = 0.0,
)

@Serializable
data class BanditStateDto(
    val arms: Map<String, BanditArmStatsDto> = emptyMap(),
    val lastArm: String? = null,
)

@Serializable
data class RecommendationWeightsDto(
    val genre: Double = 1.0,
    val artist: Double = 0.8,
    val features: Double = 0.6,
    val recentSignals: Double = 0.5,
    val exploration: Double = 0.7,
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
    val transitionFit: Double = 0.45,
    val deepenRelated: Double = 0.55,
    val deepenSameArtist: Double = 0.7,
)

fun Track.toDto(): TrackDto = TrackDto(
    id = id.value,
    title = title,
    artists = artists.map { ArtistDto(it.id.value, it.name) },
    album = AlbumDto(album.id, album.name, album.artworkUrl, album.releaseYear),
    genres = genres.map { it.value },
    audioFeatures = AudioFeaturesDto(
        audioFeatures.energy,
        audioFeatures.danceability,
        audioFeatures.valence,
        audioFeatures.tempo,
    ),
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    previewUrl = previewUrl,
    popularity = popularity,
    featureConfidence = featureConfidence,
    explicit = explicit,
)

fun TrackDto.toDomain(): Track = Track(
    id = TrackId(id),
    title = title,
    artists = artists.map { Artist(ArtistId(it.id), it.name) },
    album = Album(album.id, album.name, album.artworkUrl, album.releaseYear),
    genres = genres.map(::GenreId),
    audioFeatures = AudioFeatures(
        audioFeatures.energy,
        audioFeatures.danceability,
        audioFeatures.valence,
        audioFeatures.tempo,
    ),
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    previewUrl = previewUrl,
    popularity = popularity,
    featureConfidence = featureConfidence,
    explicit = explicit,
)

fun Track.encodeJson(): String = PersistenceJson.encodeToString(toDto())

fun decodeTrackJson(json: String): Track = PersistenceJson.decodeFromString<TrackDto>(json).toDomain()

fun HistoryEntry.toDto(): HistoryEntryDto = HistoryEntryDto(
    track = track.toDto(),
    discoveryMode = discoveryMode.name,
    recommendedAtEpochMs = recommendedAtEpochMs,
    listeningProgress = listeningProgress,
)

fun HistoryEntryDto.toDomain(): HistoryEntry = HistoryEntry(
    track = track.toDomain(),
    discoveryMode = runCatching { DiscoveryMode.valueOf(discoveryMode) }.getOrDefault(DiscoveryMode.Normal),
    recommendedAtEpochMs = recommendedAtEpochMs,
    listeningProgress = listeningProgress,
)

fun HistoryEntry.encodeJson(): String = PersistenceJson.encodeToString(toDto())

/** Accepts legacy bare TrackDto JSON or HistoryEntryDto JSON. */
fun decodeHistoryEntryJson(json: String): HistoryEntry = runCatching {
    PersistenceJson.decodeFromString<HistoryEntryDto>(json).toDomain()
}.getOrElse {
    HistoryEntry(track = decodeTrackJson(json))
}

fun UserPreferenceProfile.toDto(): PreferenceProfileDto = PreferenceProfileDto(
    genreScores = genreScores.mapKeys { it.key.value },
    artistScores = artistScores.mapKeys { it.key.value },
    genreListeningScores = genreListeningScores.mapKeys { it.key.value },
    artistListeningScores = artistListeningScores.mapKeys { it.key.value },
    timeBucketGenreScores = timeBucketGenreScores.mapKeys { it.key.toString() }
        .mapValues { entry -> entry.value.mapKeys { it.key.value } },
    timeBucketSampleCounts = timeBucketSampleCounts.mapKeys { it.key.toString() },
    weekendGenreScores = weekendGenreScores.mapKeys { it.key.value },
    weekdayGenreScores = weekdayGenreScores.mapKeys { it.key.value },
    weekendSampleCount = weekendSampleCount,
    weekdaySampleCount = weekdaySampleCount,
    preferredFeatures = preferredFeatures?.let {
        AudioFeaturesDto(it.energy, it.danceability, it.valence, it.tempo)
    },
    explorationPreference = explorationPreference,
    positiveSignalCount = positiveSignalCount,
    negativeSignalCount = negativeSignalCount,
)

fun PreferenceProfileDto.toDomain(): UserPreferenceProfile = UserPreferenceProfile(
    genreScores = genreScores.mapKeys { GenreId(it.key) },
    artistScores = artistScores.mapKeys { ArtistId(it.key) },
    genreListeningScores = genreListeningScores.mapKeys { GenreId(it.key) },
    artistListeningScores = artistListeningScores.mapKeys { ArtistId(it.key) },
    timeBucketGenreScores = timeBucketGenreScores.mapKeys { it.key.toInt() }
        .mapValues { entry -> entry.value.mapKeys { GenreId(it.key) } },
    timeBucketSampleCounts = timeBucketSampleCounts.mapKeys { it.key.toInt() },
    weekendGenreScores = weekendGenreScores.mapKeys { GenreId(it.key) },
    weekdayGenreScores = weekdayGenreScores.mapKeys { GenreId(it.key) },
    weekendSampleCount = weekendSampleCount,
    weekdaySampleCount = weekdaySampleCount,
    preferredFeatures = preferredFeatures?.let {
        AudioFeatures(it.energy, it.danceability, it.valence, it.tempo)
    },
    explorationPreference = explorationPreference,
    positiveSignalCount = positiveSignalCount,
    negativeSignalCount = negativeSignalCount,
)

fun UserPreferenceProfile.encodeJson(): String = PersistenceJson.encodeToString(toDto())

fun decodePreferenceJson(json: String): UserPreferenceProfile =
    PersistenceJson.decodeFromString<PreferenceProfileDto>(json).toDomain()

fun AppSettings.toDto(): AppSettingsDto = AppSettingsDto(
    historyMaxSize = historyConfig.maxSize,
    weights = RecommendationWeightsDto(
        genre = recommendationWeights.genre,
        artist = recommendationWeights.artist,
        features = recommendationWeights.features,
        recentSignals = recommendationWeights.recentSignals,
        exploration = recommendationWeights.exploration,
        listening = recommendationWeights.listening,
        listeningBehavior = recommendationWeights.listeningBehavior,
        contextualTime = recommendationWeights.contextualTime,
        similarity = recommendationWeights.similarity,
        artistDiversity = recommendationWeights.artistDiversity,
        trackRepetition = recommendationWeights.trackRepetition,
        artistFocusRelief = recommendationWeights.artistFocusRelief,
        negativePenalty = recommendationWeights.negativePenalty,
        coldStart = recommendationWeights.coldStart,
        sessionAffinity = recommendationWeights.sessionAffinity,
        artistGraph = recommendationWeights.artistGraph,
        popularityFit = recommendationWeights.popularityFit,
        eraFit = recommendationWeights.eraFit,
        localSimilarity = recommendationWeights.localSimilarity,
        transitionFit = recommendationWeights.transitionFit,
        deepenRelated = recommendationWeights.deepenRelated,
        deepenSameArtist = recommendationWeights.deepenSameArtist,
    ),
    bandit = BanditStateDto(
        arms = banditState.arms.mapKeys { it.key.name }.mapValues {
            BanditArmStatsDto(pulls = it.value.pulls, totalReward = it.value.totalReward)
        },
        lastArm = banditState.lastArm?.name,
    ),
)

fun AppSettingsDto.toDomain(): AppSettings = AppSettings(
    historyConfig = HistoryConfig(maxSize = historyMaxSize),
    recommendationWeights = RecommendationWeights(
        genre = weights.genre,
        artist = weights.artist,
        features = weights.features,
        recentSignals = weights.recentSignals,
        exploration = weights.exploration,
        listening = weights.listening,
        listeningBehavior = weights.listeningBehavior,
        contextualTime = weights.contextualTime,
        similarity = weights.similarity,
        artistDiversity = weights.artistDiversity,
        trackRepetition = weights.trackRepetition,
        artistFocusRelief = weights.artistFocusRelief,
        negativePenalty = weights.negativePenalty,
        coldStart = weights.coldStart,
        sessionAffinity = weights.sessionAffinity,
        artistGraph = weights.artistGraph,
        popularityFit = weights.popularityFit,
        eraFit = weights.eraFit,
        localSimilarity = weights.localSimilarity,
        transitionFit = weights.transitionFit,
        deepenRelated = weights.deepenRelated,
        deepenSameArtist = weights.deepenSameArtist,
    ),
    banditState = ContextualWeightPolicyState(
        arms = WeightArm.entries.associateWith { arm ->
            val dto = bandit.arms[arm.name]
            BanditArmStats(pulls = dto?.pulls ?: 0, totalReward = dto?.totalReward ?: 0.0)
        },
        lastArm = bandit.lastArm?.let { runCatching { WeightArm.valueOf(it) }.getOrNull() },
    ),
)

fun AppSettings.encodeJson(): String = PersistenceJson.encodeToString(toDto())

fun decodeSettingsJson(json: String): AppSettings =
    PersistenceJson.decodeFromString<AppSettingsDto>(json).toDomain()

fun TrackInteraction.toEntityFields(): InteractionFields = InteractionFields(
    id = id.value,
    trackId = trackId.value,
    interactionType = interactionType.name,
    verticalIntent = verticalIntent.name,
    horizontalIntent = horizontalIntent.name,
    gestureIntensity = gestureIntensity.name,
    gestureVelocity = gestureVelocity,
    listeningDurationMs = listeningDurationMs,
    timestampEpochMs = timestampEpochMs,
    artistId = artistId?.value,
    trackDurationMs = trackDurationMs,
    listeningProgress = listeningProgress,
    discoveryMode = discoveryMode.name,
    localHour = localHour,
    dayOfWeek = dayOfWeek,
    navigationAction = navigationAction.name,
)

data class InteractionFields(
    val id: String,
    val trackId: String,
    val interactionType: String,
    val verticalIntent: String,
    val horizontalIntent: String,
    val gestureIntensity: String,
    val gestureVelocity: Double,
    val listeningDurationMs: Long,
    val timestampEpochMs: Long,
    val artistId: String? = null,
    val trackDurationMs: Long? = null,
    val listeningProgress: Double = 0.0,
    val discoveryMode: String = DiscoveryMode.Normal.name,
    val localHour: Int = 12,
    val dayOfWeek: Int = 1,
    val navigationAction: String = NavigationAction.None.name,
)

fun InteractionFields.toDomain(): TrackInteraction = TrackInteraction(
    id = InteractionId(id),
    trackId = TrackId(trackId),
    interactionType = runCatching { InteractionType.valueOf(interactionType) }
        .getOrDefault(InteractionType.Preference),
    verticalIntent = runCatching { VerticalIntent.valueOf(verticalIntent) }
        .getOrDefault(VerticalIntent.None),
    horizontalIntent = runCatching { HorizontalIntent.valueOf(horizontalIntent) }
        .getOrDefault(HorizontalIntent.None),
    gestureIntensity = runCatching { GestureIntensity.valueOf(gestureIntensity) }
        .getOrDefault(GestureIntensity.Medium),
    gestureVelocity = gestureVelocity,
    listeningDurationMs = listeningDurationMs,
    timestampEpochMs = timestampEpochMs,
    artistId = artistId?.let(::ArtistId),
    trackDurationMs = trackDurationMs,
    listeningProgress = listeningProgress.coerceIn(0.0, 1.0),
    discoveryMode = runCatching { DiscoveryMode.valueOf(discoveryMode) }
        .getOrDefault(DiscoveryMode.Normal),
    localHour = localHour.coerceIn(0, 23),
    dayOfWeek = dayOfWeek.coerceIn(1, 7),
    navigationAction = runCatching { NavigationAction.valueOf(navigationAction) }
        .getOrDefault(NavigationAction.None),
)
