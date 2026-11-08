package com.sensorial.domain.recommendation

import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.Track

/** Candidate tracks plus artist-graph metadata used by the scorer. */
data class CandidatePool(
    val tracks: List<Track>,
    val relatedArtistIds: Set<ArtistId> = emptySet(),
)
