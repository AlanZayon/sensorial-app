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
 * Mode-aware candidate pool with explicit retrieval arms:
 * - profileTopArtists / relatedGraph / seedGenreExplore / eraYearWindow / currentAlbumNeighbors
 *
 * Profiles:
 * - [FetchProfile.Interactive]: critical swipe path — early exit once enough quality tracks exist
 * - [FetchProfile.Prefetch]: background warm — richer union pool for the active discovery mode
 */
class CandidateQueryBuilder(
    private val api: SpotifyWebApi,
    private val targetPoolSize: Int = 70,
    private val diversity: DiversityConfig = DiversityConfig(),
) {
    enum class FetchProfile {
        /** Swipe miss: prioritize time-to-first-rankable-pool. */
        Interactive,
        /** Background: broader pool for the request's discovery mode. */
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
        val secondOrderRelated = linkedSetOf<ArtistId>()

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
            context.discoveryMode == DiscoveryMode.Explore -> if (profile == FetchProfile.Prefetch) 8 else 5
            context.discoveryMode == DiscoveryMode.Deepen -> 6
            profile == FetchProfile.Interactive -> 3
            else -> 4
        }
        val seedArtistLimit = when {
            context.discoveryMode == DiscoveryMode.Explore -> if (profile == FetchProfile.Prefetch) 4 else 3
            profile == FetchProfile.Interactive -> 2
            else -> 3
        }
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
        val exploreGenres = if (context.discoveryMode == DiscoveryMode.Explore) {
            (genres + EXPLORE_SEED_GENRES).distinctBy { it.value }.take(5)
        } else {
            genres
        }
        val seedArtists = context.topArtists.take(seedArtistLimit)
        val currentArtistId = current?.primaryArtist?.id?.value
        val eraQuery = eraYearWindow(context)

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
        val genreJobs = exploreGenres.map { genre ->
            async {
                val limit = when {
                    context.discoveryMode == DiscoveryMode.Explore -> if (profile == FetchProfile.Prefetch) 18 else 14
                    profile == FetchProfile.Interactive -> 12
                    else -> 15
                }
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
        val eraJob = async {
            if (eraQuery == null) emptyList()
            else runCatching { api.searchTracks(eraQuery, limit = 12) }.getOrDefault(emptyList())
        }

        addAll(artistTop.await())
        addAll(artistSearch.await())
        genreJobs.awaitAll().forEach(::addAll)
        seedTopJobs.awaitAll().forEach(::addAll)
        addAll(currentGenreJob.await())
        addAll(eraJob.await())

        val related = relatedJob.await()
        related.forEach { relatedIds += it.id }

        // Wave 2 — related artist tops (always for Deepen/Explore; otherwise if thin/prefetch).
        if (!readyEnough(minReady) || profile == FetchProfile.Prefetch ||
            context.discoveryMode == DiscoveryMode.Deepen ||
            context.discoveryMode == DiscoveryMode.Explore
        ) {
            val relatedTops = related.take(relatedTopLimit).map { artist ->
                async {
                    val tops = runCatching { api.artistTopTracks(artist.id.value) }.getOrDefault(emptyList())
                    if (context.discoveryMode == DiscoveryMode.Deepen) tops.take(4) else tops
                }
            }
            relatedTops.awaitAll().forEach(::addAll)
        }

        // Explore: second-order related graph for novelty.
        if (context.discoveryMode == DiscoveryMode.Explore &&
            (profile == FetchProfile.Prefetch || !readyEnough(minReady))
        ) {
            val hubs = related.take(3)
            val second = hubs.map { artist ->
                async {
                    runCatching { api.relatedArtists(artist.id.value) }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
            second.forEach { secondOrderRelated += it.id }
            relatedIds += secondOrderRelated
            second.take(5).map { artist ->
                async {
                    runCatching { api.artistTopTracks(artist.id.value) }.getOrDefault(emptyList()).take(3)
                }
            }.awaitAll().forEach(::addAll)
        }

        // Wave 3 — albums only when needed (expensive multi-hop) — currentAlbumNeighbors.
        if (includeAlbums && !currentArtistId.isNullOrBlank() &&
            (profile == FetchProfile.Prefetch || !readyEnough(minReady) ||
                context.discoveryMode == DiscoveryMode.Deepen)
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
            .sortedWith(
                compareByDescending<Track> { !it.previewUrl.isNullOrBlank() }
                    .thenByDescending {
                        // Prefer real audio-features once the pool is large enough.
                        if (collected.size >= minReady) it.featureConfidence else 0.0
                    }
                    .thenByDescending { it.popularity },
            )
            .take(targetPoolSize)
        FetchResult(tracks = tracks, relatedArtistIds = relatedIds)
    }

    private fun eraYearWindow(context: RecommendationContext): String? {
        val years = context.recentTracks.mapNotNull { it.releaseYear }
        if (years.isEmpty()) {
            return if (context.discoveryMode == DiscoveryMode.Explore) "year:2015-2025" else null
        }
        val mean = years.average().toInt()
        val span = when (context.discoveryMode) {
            DiscoveryMode.Deepen -> 4
            DiscoveryMode.Explore -> 12
            DiscoveryMode.Normal -> 7
        }
        val start = (mean - span).coerceAtLeast(1950)
        val end = (mean + span).coerceAtMost(2030)
        return "year:$start-$end"
    }

    companion object {
        val DEFAULT_SEED_GENRES = listOf(
            GenreId("pop"),
            GenreId("indie"),
            GenreId("electronic"),
        )
        val EXPLORE_SEED_GENRES = listOf(
            GenreId("alternative"),
            GenreId("experimental"),
            GenreId("world"),
        )
        val FALLBACK_QUERIES = listOf(
            "year:2020-2025",
            "genre:\"indie\"",
            "genre:\"pop\"",
        )
    }
}
