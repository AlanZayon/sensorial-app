package com.sensorial.domain.recommendation

import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.Track
import kotlin.math.sqrt

/**
 * Pluggable track similarity. Default is a local feature vector (no remote embeddings).
 * A future remote embedding model can implement the same interface.
 */
fun interface TrackSimilarity {
    fun similarity(a: Track, b: Track, genreVocabulary: List<GenreId>): Double
}

/**
 * Cosine similarity over [energy, dance, valence, tempoNorm, genre one-hot top-K].
 */
class LocalFeatureTrackSimilarity(
    private val maxGenres: Int = 8,
) : TrackSimilarity {
    fun similarity(a: Track, b: Track): Double = similarity(a, b, emptyList())

    override fun similarity(a: Track, b: Track, genreVocabulary: List<GenreId>): Double {
        val vocab = genreVocabulary.ifEmpty {
            (a.genres + b.genres).distinct().take(maxGenres)
        }.take(maxGenres)
        val va = vector(a, vocab)
        val vb = vector(b, vocab)
        return cosine(va, vb).coerceIn(0.0, 1.0)
    }

    private fun vector(track: Track, vocab: List<GenreId>): DoubleArray {
        val f = track.audioFeatures
        val tempoNorm = ((f.tempo - 60.0) / 140.0).coerceIn(0.0, 1.0)
        val base = doubleArrayOf(f.energy, f.danceability, f.valence, tempoNorm)
        val genreBits = DoubleArray(vocab.size) { i ->
            if (track.genres.any { it == vocab[i] }) 1.0 else 0.0
        }
        return base + genreBits
    }

    private fun cosine(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }
}
