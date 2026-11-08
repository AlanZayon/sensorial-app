import Foundation

public protocol TrackSimilarity: Sendable {
    func similarity(_ a: Track, _ b: Track, genreVocabulary: [GenreId]) -> Double
}

public struct LocalFeatureTrackSimilarity: TrackSimilarity {
    private let maxGenres: Int

    public init(maxGenres: Int = 8) {
        self.maxGenres = maxGenres
    }

    public func similarity(_ a: Track, _ b: Track, genreVocabulary: [GenreId] = []) -> Double {
        let vocab = (genreVocabulary.isEmpty ? Array(Set(a.genres + b.genres)) : genreVocabulary)
            .prefix(maxGenres)
        let va = vector(a, vocab: Array(vocab))
        let vb = vector(b, vocab: Array(vocab))
        return cosine(va, vb)
    }

    private func vector(_ track: Track, vocab: [GenreId]) -> [Double] {
        let f = track.audioFeatures
        let tempoNorm = min(1, max(0, (f.tempo - 60) / 140))
        var values = [f.energy, f.danceability, f.valence, tempoNorm]
        for g in vocab {
            values.append(track.genres.contains(g) ? 1 : 0)
        }
        return values
    }

    private func cosine(_ a: [Double], _ b: [Double]) -> Double {
        var dot = 0.0, na = 0.0, nb = 0.0
        for i in a.indices {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        guard na > 0, nb > 0 else { return 0 }
        return min(1, max(0, dot / (sqrt(na) * sqrt(nb))))
    }
}
