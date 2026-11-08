import Foundation
import SensorialDomain

public enum AudioFeatureFallback {
    public static func forGenres(_ genres: [GenreId]) -> AudioFeatures {
        let keys = genres.map { $0.value.lowercased() }
        if keys.contains(where: { $0.contains("metal") || $0.contains("punk") }) {
            return AudioFeatures(energy: 0.85, danceability: 0.45, valence: 0.35, tempo: 140)
        }
        if keys.contains(where: { $0.contains("electronic") || $0.contains("dance") || $0.contains("edm") || $0.contains("house") }) {
            return AudioFeatures(energy: 0.80, danceability: 0.85, valence: 0.55, tempo: 128)
        }
        if keys.contains(where: { $0.contains("hip") || $0.contains("rap") }) {
            return AudioFeatures(energy: 0.70, danceability: 0.75, valence: 0.45, tempo: 95)
        }
        if keys.contains(where: { $0.contains("jazz") }) {
            return AudioFeatures(energy: 0.40, danceability: 0.35, valence: 0.50, tempo: 110)
        }
        if keys.contains(where: { $0.contains("ambient") || $0.contains("classical") }) {
            return AudioFeatures(energy: 0.25, danceability: 0.20, valence: 0.40, tempo: 80)
        }
        if keys.contains(where: { $0.contains("folk") || $0.contains("acoustic") }) {
            return AudioFeatures(energy: 0.35, danceability: 0.40, valence: 0.55, tempo: 105)
        }
        if keys.contains(where: { $0.contains("latin") || $0.contains("reggaeton") }) {
            return AudioFeatures(energy: 0.75, danceability: 0.80, valence: 0.70, tempo: 100)
        }
        if keys.contains(where: { $0.contains("rock") }) {
            return AudioFeatures(energy: 0.75, danceability: 0.55, valence: 0.50, tempo: 125)
        }
        if keys.contains(where: { $0.contains("indie") || $0.contains("alternative") }) {
            return AudioFeatures(energy: 0.55, danceability: 0.50, valence: 0.45, tempo: 118)
        }
        if keys.contains(where: { $0.contains("pop") }) {
            return AudioFeatures(energy: 0.65, danceability: 0.70, valence: 0.60, tempo: 120)
        }
        return AudioFeatures(energy: 0.55, danceability: 0.55, valence: 0.50, tempo: 118)
    }
}
