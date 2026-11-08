package com.sensorial.domain.model

@JvmInline
value class TrackId(val value: String) {
    init {
        require(value.isNotBlank()) { "TrackId cannot be blank" }
    }
}

@JvmInline
value class ArtistId(val value: String) {
    init {
        require(value.isNotBlank()) { "ArtistId cannot be blank" }
    }
}

@JvmInline
value class GenreId(val value: String) {
    init {
        require(value.isNotBlank()) { "GenreId cannot be blank" }
    }
}

@JvmInline
value class InteractionId(val value: String) {
    init {
        require(value.isNotBlank()) { "InteractionId cannot be blank" }
    }
}
