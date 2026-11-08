package com.sensorial.data.spotify

import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.Track
import com.sensorial.domain.recommendation.DiversityConfig
import com.sensorial.domain.recommendation.RecentVersionBlock
import com.sensorial.domain.recommendation.RecommendationContext
import com.sensorial.domain.recommendation.TrackFingerprint
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Mode-aware candidate pool with two latency profiles:
 * - [FetchProfile.Interactive]: critical swipe path — early exit once enough quality tracks exist
 * - [FetchProfile.Prefetch]: background warm — richer union pool so Deepen/Explore hit cache
 */
class CandidateQueryBuilder(
    private val api: SpotifyWebApi,
    private val targetPoolSize: Int = 70,
    private val diversity: DiversityConfig = DiversityConfig(),
) {
    enum class FetchProfile {
        /** Swipe miss: prioritize time-to-first-rankable-pool. */
        Interactive,
        /** Background: broader pool shared by all discovery modes. */
        Prefetch,
    }

    data class FetchResult(
        val tracks: List<Track>,
        val relatedArtistIds: Set<ArtistId>,
    )

    suspend fun fetch(context: RecommendationContext): List<Track> =
        fetchDetailed(context, FetchProfile.Interactive).tracks

    suspend fun fetchDetailed(
        context: RecommendationContext,
        profile: FetchProfile = FetchProfile.Interactive,
    ): FetchResult = coroutineScope {
        val collected = LinkedHashMap<String, Track>()
        val excludedIds = context.excludedIds
        val blockedFingerprints = RecentVersionBlock.fingerprints(
            recentTracks = context.recentTracks,
            currentTrack = context.currentTrack,
            config = diversity,
        ) + context.blockedFingerprints
        val relatedIds = linkedSetOf<ArtistId>()

        fun addAll(tracks: List<Track>) {
            for (track in tracks) {
                if (track.id in excludedIds) continue
                val fingerprint = TrackFingerprint.of(track)
                if (fingerprint in blockedFingerprints) continue
                if (fingerprint in collected) continue
                collected[fingerprint] = track
            }
        }

        fun readyEnough(minSize: Int): Boolean {
            if (collected.size < minSize) return false
            return collected.values.any { !it.previewUrl.isNullOrBlank() } || collected.size >= minSize + 10
        }

        val minReady = when (profile) {
            FetchProfile.Interactive -> 28
            FetchProfile.Prefetch -> 48
        }
        val relatedTopLimit = when {
            context.discoveryMode == DiscoveryMode.Deepen -> 6
            profile == FetchProfile.Interactive -> 3
            else -> 4
        }
        val seedArtistLimit = if (profile == FetchProfile.Interactive) 2 else 3
        val includeAlbums = profile == FetchProfile.Prefetch ||
            context.discoveryMode == DiscoveryMode.Deepen
        // Deepen should deepen the climate via related artists, not catalog-dump one artist.
        val sameArtistSearchLimit = when (context.discoveryMode) {
            DiscoveryMode.Deepen -> 6
            DiscoveryMode.Explore -> 0
            DiscoveryMode.Normal -> 15
        }

        val current = context.currentTrack
        val genres = context.seedGenres.ifEmpty { DEFAULT_SEED_GENRES }
        val seedArtists = context.topArtists.take(seedArtistLimit)
        val currentArtistId = current?.primaryArtist?.id?.value

        // Wave 1 — independent lookups in parallel (no related dependency).
        val artistTop = async {
            if (currentArtistId.isNullOrBlank()) emptyList()
            else {
                val tops = runCatching { api.artistTopTracks(currentArtistId) }.getOrDefault(emptyList())
                if (context.discoveryMode == DiscoveryMode.Deepen) tops.take(5) else tops
            }
        }
        val artistSearch = async {
            val name = current?.primaryArtist?.name ?: return@async emptyList()
            if (sameArtistSearchLimit <= 0) return@async emptyList()
            if (context.discoveryMode == DiscoveryMode.Explore && profile == FetchProfile.Interactive) {
                return@async emptyList()
            }
            runCatching {
                api.searchTracks("artist:\"$name\"", limit = sameArtistSearchLimit)
            }.getOrDefault(emptyList())
        }
        val genreJobs = genres.map { genre ->
            async {
                val limit = if (profile == FetchProfile.Interactive) 12 else 15
                runCatching { api.searchTracks("genre:\"${genre.value}\"", limit = limit) }
                    .getOrDefault(emptyList())
            }
        }
        val seedTopJobs = seedArtists.map { artistId ->
            async {
                runCatching { api.artistTopTracks(artistId.value) }.getOrDefault(emptyList())
            }
        }
        val relatedJob = async {
            if (currentArtistId.isNullOrBlank()) emptyList()
            else runCatching { api.relatedArtists(currentArtistId) }.getOrDefault(emptyList())
        }
        val currentGenreJob = async {
            val g = current?.genres?.firstOrNull()?.value ?: return@async emptyList()
            if (context.discoveryMode == DiscoveryMode.Explore && profile == FetchProfile.Interactive) {
                return@async emptyList()
            }
            runCatching { api.searchTracks("genre:\"$g\"", limit = 15) }.getOrDefault(emptyList())
        }

        addAll(artistTop.await())
        addAll(artistSearch.await())
        genreJobs.awaitAll().forEach(::addAll)
        seedTopJobs.awaitAll().forEach(::addAll)
        addAll(currentGenreJob.await())

        val related = relatedJob.await()
        related.forEach { relatedIds += it.id }

        // Wave 2 — related artist tops (always for Deepen; otherwise if thin/prefetch).
        if (!readyEnough(minReady) || profile == FetchProfile.Prefetch ||
            context.discoveryMode == DiscoveryMode.Deepen
        ) {
            val relatedTops = related.take(relatedTopLimit).map { artist ->
                async {
                    val tops = runCatching { api.artistTopTracks(artist.id.value) }.getOrDefault(emptyList())
                    if (context.discoveryMode == DiscoveryMode.Deepen) tops.take(4) else tops
                }
            }
            relatedTops.awaitAll().forEach(::addAll)
        }

        // Wave 3 — albums only when needed (expensive multi-hop).
        if (includeAlbums && !currentArtistId.isNullOrBlank() &&
            (profile == FetchProfile.Prefetch || !readyEnough(minReady))
        ) {
            addAll(
                runCatching { api.artistRecentAlbumTracks(currentArtistId, albumLimit = 1) }
                    .getOrDefault(emptyList()),
            )
        }

        // Wave 4 — fallback only if pool empty / no preview.
        if (collected.isEmpty() || collected.values.none { !it.previewUrl.isNullOrBlank() }) {
            val fallbacks = FALLBACK_QUERIES.map { query ->
                async {
                    runCatching { api.searchTracks(query, limit = 15) }.getOrDefault(emptyList())
                }
            }
            fallbacks.awaitAll().forEach(::addAll)
        }

        val tracks = collected.values
            .sortedByDescending { !it.previewUrl.isNullOrBlank() }
            .take(targetPoolSize)
        FetchResult(tracks = tracks, relatedArtistIds = relatedIds)
    }

    companion object {
        val DEFAULT_SEED_GENRES = listOf(
            GenreId("pop"),
            GenreId("indie"),
            GenreId("electronic"),
        )
        val FALLBACK_QUERIES = listOf(
            "year:2020-2025",
            "genre:\"indie\"",
            "genre:\"pop\"",
        )
    }
}
