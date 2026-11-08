package com.sensorial.data.spotify

import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class SpotifyWebApi(
    private val auth: SpotifyAuthRepository,
    private val http: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    private val featureCache = mutableMapOf<String, com.sensorial.domain.model.AudioFeatures>()
    private val artistGenreCache = mutableMapOf<String, List<GenreId>>()
    private val relatedArtistsCache = mutableMapOf<String, List<Artist>>()

    suspend fun searchTracks(query: String, limit: Int = 20): List<Track> {
        if (query.isBlank()) return emptyList()
        val url = "${SpotifyConfig.API_BASE}/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", limit.coerceIn(1, 50).toString())
            .addQueryParameter("market", "from_token")
            .build()
        val body = authorizedGet(url.toString())
        val response = json.decodeFromString<SpotifySearchResponse>(body)
        return mapTracks(response.tracks?.items.orEmpty())
    }

    suspend fun getTrack(id: TrackId): Track? =
        getTracks(listOf(id))[id]

    suspend fun getTracks(ids: List<TrackId>): Map<TrackId, Track> {
        val unique = ids.map { it.value }.filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) return emptyMap()
        val collected = LinkedHashMap<String, SpotifyTrackDto>()
        unique.chunked(50).forEach { chunk ->
            val url = "${SpotifyConfig.API_BASE}/tracks".toHttpUrl().newBuilder()
                .addQueryParameter("ids", chunk.joinToString(","))
                .addQueryParameter("market", "from_token")
                .build()
            val body = runCatching { authorizedGet(url.toString()) }.getOrNull() ?: return@forEach
            val response = runCatching {
                json.decodeFromString<SpotifyTracksResponse>(body)
            }.getOrNull() ?: return@forEach
            response.tracks.forEach { dto ->
                if (dto != null && dto.id.isNotBlank()) collected[dto.id] = dto
            }
        }
        if (collected.isEmpty()) return emptyMap()
        return mapTracks(collected.values.toList()).associateBy { it.id }
    }

    suspend fun artistTopTracks(artistId: String, market: String = "BR"): List<Track> {
        if (artistId.isBlank()) return emptyList()
        val url = "${SpotifyConfig.API_BASE}/artists/$artistId/top-tracks".toHttpUrl().newBuilder()
            .addQueryParameter("market", market)
            .build()
        val body = authorizedGet(url.toString())
        val response = json.decodeFromString<SpotifyTopTracksResponse>(body)
        return mapTracks(response.tracks)
    }

    suspend fun relatedArtists(artistId: String): List<Artist> {
        if (artistId.isBlank()) return emptyList()
        relatedArtistsCache[artistId]?.let { return it }
        val body = runCatching {
            authorizedGet("${SpotifyConfig.API_BASE}/artists/$artistId/related-artists")
        }.getOrNull() ?: return emptyList()
        val response = json.decodeFromString<SpotifyRelatedArtistsResponse>(body)
        val artists = response.artists
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .map { Artist(ArtistId(it.id), it.name) }
        relatedArtistsCache[artistId] = artists
        return artists
    }

    /** Recent albums (album/single) then first page of tracks for up to [albumLimit] releases. */
    suspend fun artistRecentAlbumTracks(artistId: String, albumLimit: Int = 2, market: String = "BR"): List<Track> {
        if (artistId.isBlank()) return emptyList()
        val albumsUrl = "${SpotifyConfig.API_BASE}/artists/$artistId/albums".toHttpUrl().newBuilder()
            .addQueryParameter("include_groups", "album,single")
            .addQueryParameter("limit", albumLimit.coerceIn(1, 5).toString())
            .addQueryParameter("market", market)
            .build()
        val albumsBody = runCatching { authorizedGet(albumsUrl.toString()) }.getOrNull() ?: return emptyList()
        val albums = json.decodeFromString<SpotifyAlbumsResponse>(albumsBody).items
        val collected = LinkedHashMap<String, SpotifyTrackDto>()
        for (album in albums.take(albumLimit)) {
            if (album.id.isBlank()) continue
            val tracksUrl = "${SpotifyConfig.API_BASE}/albums/${album.id}/tracks".toHttpUrl().newBuilder()
                .addQueryParameter("limit", "10")
                .addQueryParameter("market", market)
                .build()
            val tracksBody = runCatching { authorizedGet(tracksUrl.toString()) }.getOrNull() ?: continue
            val items = json.decodeFromString<SpotifyAlbumTracksResponse>(tracksBody).items
            items.forEach { simplified ->
                if (simplified.id.isBlank()) return@forEach
                collected[simplified.id] = SpotifyTrackDto(
                    id = simplified.id,
                    name = simplified.name,
                    artists = simplified.artists.ifEmpty {
                        listOf(SpotifyArtistDto(id = artistId, name = "Artist"))
                    },
                    album = album,
                    durationMs = simplified.durationMs,
                    previewUrl = simplified.previewUrl,
                    popularity = 0,
                    explicit = simplified.explicit,
                )
            }
        }
        return mapTracks(collected.values.toList())
    }

    private suspend fun mapTracks(dtos: List<SpotifyTrackDto>): List<Track> = coroutineScope {
        val artistIds = dtos.mapNotNull { it.artists.firstOrNull()?.id }.distinct()
        val genresDeferred = async { artistGenresBatch(artistIds) }
        val featuresDeferred = async { audioFeatures(dtos.map { it.id }) }
        val genresByArtist = genresDeferred.await()
        val featureMap = featuresDeferred.await()
        dtos.map { dto ->
            val genres = dto.artists.firstOrNull()?.id?.let { genresByArtist[it] }.orEmpty()
            val apiFeatures = featureMap[dto.id]
            SpotifyTrackMapper.toDomain(
                dto = dto,
                genres = genres,
                features = apiFeatures ?: AudioFeatureFallback.forGenres(genres),
                featureConfidence = if (apiFeatures != null) 1.0 else 0.35,
            )
        }
    }

    private suspend fun artistGenresBatch(artistIds: List<String>): Map<String, List<GenreId>> {
        if (artistIds.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, List<GenreId>>()
        val missing = artistIds.filter { id ->
            val cached = artistGenreCache[id]
            if (cached != null) {
                result[id] = cached
                false
            } else {
                true
            }
        }
        if (missing.isEmpty()) return result
        // Spotify /artists?ids= accepts up to 50 ids per call.
        missing.chunked(50).forEach { chunk ->
            val joined = chunk.joinToString(",")
            val body = runCatching {
                authorizedGet("${SpotifyConfig.API_BASE}/artists?ids=$joined")
            }.getOrNull()
            val artists = body?.let {
                runCatching { json.decodeFromString<SpotifyArtistsResponse>(it).artists }.getOrNull()
            }.orEmpty()
            val byId = artists.filterNotNull().associateBy { it.id }
            for (id in chunk) {
                val genres = byId[id]?.genres.orEmpty()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { GenreId(it) }
                    .ifEmpty { listOf(GenreId("unknown")) }
                artistGenreCache[id] = genres
                result[id] = genres
            }
        }
        return result
    }

    private suspend fun artistGenres(artistId: String): List<GenreId> =
        artistGenresBatch(listOf(artistId))[artistId].orEmpty()

    private suspend fun audioFeatures(ids: List<String>): Map<String, com.sensorial.domain.model.AudioFeatures> {
        val missing = ids.filter { it !in featureCache }
        if (missing.isEmpty()) {
            return ids.mapNotNull { id -> featureCache[id]?.let { id to it } }.toMap()
        }
        missing.chunked(50).forEach { chunk ->
            val joined = chunk.joinToString(",")
            val result = runCatching {
                val body = authorizedGet("${SpotifyConfig.API_BASE}/audio-features?ids=$joined")
                json.decodeFromString<SpotifyAudioFeaturesListDto>(body)
            }.getOrNull()
            result?.audioFeatures?.forEach { dto ->
                if (dto?.id != null && dto.energy != null && dto.danceability != null &&
                    dto.valence != null && dto.tempo != null && dto.tempo > 0
                ) {
                    featureCache[dto.id] = AudioFeatureFallback.fromApiOrFallback(
                        dto.energy, dto.danceability, dto.valence, dto.tempo, emptyList(),
                    )
                }
            }
        }
        return ids.mapNotNull { id -> featureCache[id]?.let { id to it } }.toMap()
    }

    private suspend fun authorizedGet(url: String): String = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Spotify API ${response.code}: ${body.take(200)}")
            }
            body
        }
    }
}
