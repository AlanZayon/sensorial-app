package com.sensorial.domain.preference

import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction

data class LikedTrackRef(
    val trackId: TrackId,
    val likedAtEpochMs: Long,
)

enum class LikedSort {
    /** Newest likes first (default library order). */
    Recent,
    Title,
    Artist,
}

/**
 * Folds like/unlike events into the current liked set, newest likes first.
 */
object LikedTracks {
    fun current(interactions: List<TrackInteraction>): List<LikedTrackRef> {
        val likedAt = LinkedHashMap<TrackId, Long>()
        interactions.sortedBy { it.timestampEpochMs }.forEach { interaction ->
            when (interaction.interactionType) {
                InteractionType.ExplicitLike -> likedAt[interaction.trackId] = interaction.timestampEpochMs
                InteractionType.ExplicitUnlike -> likedAt.remove(interaction.trackId)
                else -> Unit
            }
        }
        return likedAt.entries
            .sortedByDescending { it.value }
            .map { LikedTrackRef(trackId = it.key, likedAtEpochMs = it.value) }
    }

    fun filter(
        tracks: List<Track>,
        query: String = "",
        artistId: ArtistId? = null,
        genreId: GenreId? = null,
        sort: LikedSort = LikedSort.Recent,
        likedAtById: Map<TrackId, Long> = emptyMap(),
    ): List<Track> {
        val normalized = query.trim().lowercase()
        val filtered = tracks.filter { track ->
            val matchesQuery = normalized.isEmpty() ||
                track.title.lowercase().contains(normalized) ||
                track.artists.any { it.name.lowercase().contains(normalized) } ||
                track.album.name.lowercase().contains(normalized) ||
                track.genres.any { it.value.lowercase().contains(normalized) }
            val matchesArtist = artistId == null || track.artists.any { it.id == artistId }
            val matchesGenre = genreId == null || track.genres.any { it == genreId }
            matchesQuery && matchesArtist && matchesGenre
        }
        return when (sort) {
            LikedSort.Recent -> filtered.sortedByDescending { likedAtById[it.id] ?: Long.MIN_VALUE }
            LikedSort.Title -> filtered.sortedBy { it.title.lowercase() }
            LikedSort.Artist -> filtered.sortedWith(
                compareBy(
                    { it.primaryArtist.name.lowercase() },
                    { it.title.lowercase() },
                ),
            )
        }
    }

    /** Human-readable genre label for chips and search. */
    fun genreLabel(genreId: GenreId): String =
        genreId.value
            .replace('-', ' ')
            .replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
            }
}
