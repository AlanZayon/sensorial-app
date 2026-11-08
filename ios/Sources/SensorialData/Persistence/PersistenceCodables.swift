import Foundation
import SensorialDomain

struct TrackDTO: Codable, Equatable {
    var id: String
    var title: String
    var artists: [ArtistDTO]
    var album: AlbumDTO
    var genres: [String]
    var audioFeatures: AudioFeaturesDTO
    var artworkURL: String?
    var durationMs: Int64
    var previewURL: String?
}

struct ArtistDTO: Codable, Equatable {
    var id: String
    var name: String
}

struct AlbumDTO: Codable, Equatable {
    var id: String
    var name: String
    var artworkURL: String?
}

struct AudioFeaturesDTO: Codable, Equatable {
    var energy: Double
    var danceability: Double
    var valence: Double
    var tempo: Double
}

struct PreferenceProfileDTO: Codable, Equatable {
    var genreScores: [String: Double]
    var artistScores: [String: Double]
    var genreListeningScores: [String: Double]?
    var artistListeningScores: [String: Double]?
    var timeBucketGenreScores: [String: [String: Double]]?
    var timeBucketSampleCounts: [String: Int]?
    var preferredFeatures: AudioFeaturesDTO?
    var explorationPreference: Double
    var positiveSignalCount: Int
    var negativeSignalCount: Int
}

struct AppSettingsDTO: Codable, Equatable {
    var historyMaxSize: Int
    var weights: RecommendationWeightsDTO
}

struct RecommendationWeightsDTO: Codable, Equatable {
    var genre: Double
    var artist: Double
    var features: Double
    var recentSignals: Double
    var exploration: Double
    var listening: Double
    var negativePenalty: Double
    var coldStart: Double
}

struct HistoryEntryDTO: Codable, Equatable {
    var track: TrackDTO
    var discoveryMode: String
    var recommendedAtEpochMs: Int64
    var listeningProgress: Double?
}

enum PersistenceCodec {
    static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return encoder
    }()

    static let decoder = JSONDecoder()

    static func encodeTrack(_ track: Track) throws -> Data {
        try encoder.encode(track.toDTO())
    }

    static func decodeTrack(_ data: Data) throws -> Track {
        try decoder.decode(TrackDTO.self, from: data).toDomain()
    }

    static func encodeHistoryEntry(_ entry: HistoryEntry) throws -> Data {
        try encoder.encode(HistoryEntryDTO(
            track: entry.track.toDTO(),
            discoveryMode: entry.discoveryMode.rawValue,
            recommendedAtEpochMs: entry.recommendedAtEpochMs,
            listeningProgress: entry.listeningProgress
        ))
    }

    /// Accepts legacy bare TrackDTO JSON or HistoryEntryDTO JSON.
    static func decodeHistoryEntry(_ data: Data) throws -> HistoryEntry {
        if let dto = try? decoder.decode(HistoryEntryDTO.self, from: data) {
            return HistoryEntry(
                track: dto.track.toDomain(),
                discoveryMode: DiscoveryMode(rawValue: dto.discoveryMode) ?? .normal,
                recommendedAtEpochMs: dto.recommendedAtEpochMs,
                listeningProgress: dto.listeningProgress
            )
        }
        return HistoryEntry(track: try decodeTrack(data))
    }

    static func encodeProfile(_ profile: UserPreferenceProfile) throws -> Data {
        try encoder.encode(profile.toDTO())
    }

    static func decodeProfile(_ data: Data) throws -> UserPreferenceProfile {
        try decoder.decode(PreferenceProfileDTO.self, from: data).toDomain()
    }

    static func encodeSettings(_ settings: AppSettings) throws -> Data {
        try encoder.encode(settings.toDTO())
    }

    static func decodeSettings(_ data: Data) throws -> AppSettings {
        try decoder.decode(AppSettingsDTO.self, from: data).toDomain()
    }
}

extension Track {
    func toDTO() -> TrackDTO {
        TrackDTO(
            id: id.value,
            title: title,
            artists: artists.map { ArtistDTO(id: $0.id.value, name: $0.name) },
            album: AlbumDTO(id: album.id, name: album.name, artworkURL: album.artworkURL?.absoluteString),
            genres: genres.map(\.value),
            audioFeatures: AudioFeaturesDTO(
                energy: audioFeatures.energy,
                danceability: audioFeatures.danceability,
                valence: audioFeatures.valence,
                tempo: audioFeatures.tempo
            ),
            artworkURL: artworkURL?.absoluteString,
            durationMs: durationMs,
            previewURL: previewURL?.absoluteString
        )
    }
}

extension TrackDTO {
    func toDomain() -> Track {
        Track(
            id: TrackId(id),
            title: title,
            artists: artists.map { Artist(id: ArtistId($0.id), name: $0.name) },
            album: Album(id: album.id, name: album.name, artworkURL: album.artworkURL.flatMap(URL.init(string:))),
            genres: genres.map(GenreId.init),
            audioFeatures: AudioFeatures(
                energy: audioFeatures.energy,
                danceability: audioFeatures.danceability,
                valence: audioFeatures.valence,
                tempo: audioFeatures.tempo
            ),
            artworkURL: artworkURL.flatMap(URL.init(string:)),
            durationMs: durationMs,
            previewURL: previewURL.flatMap(URL.init(string:))
        )
    }
}

extension UserPreferenceProfile {
    func toDTO() -> PreferenceProfileDTO {
        PreferenceProfileDTO(
            genreScores: Dictionary(uniqueKeysWithValues: genreScores.map { ($0.key.value, $0.value) }),
            artistScores: Dictionary(uniqueKeysWithValues: artistScores.map { ($0.key.value, $0.value) }),
            genreListeningScores: Dictionary(uniqueKeysWithValues: genreListeningScores.map { ($0.key.value, $0.value) }),
            artistListeningScores: Dictionary(uniqueKeysWithValues: artistListeningScores.map { ($0.key.value, $0.value) }),
            timeBucketGenreScores: Dictionary(uniqueKeysWithValues: timeBucketGenreScores.map { bucket, scores in
                (String(bucket), Dictionary(uniqueKeysWithValues: scores.map { ($0.key.value, $0.value) }))
            }),
            timeBucketSampleCounts: Dictionary(uniqueKeysWithValues: timeBucketSampleCounts.map { (String($0.key), $0.value) }),
            preferredFeatures: preferredFeatures.map {
                AudioFeaturesDTO(
                    energy: $0.energy,
                    danceability: $0.danceability,
                    valence: $0.valence,
                    tempo: $0.tempo
                )
            },
            explorationPreference: explorationPreference,
            positiveSignalCount: positiveSignalCount,
            negativeSignalCount: negativeSignalCount
        )
    }
}

extension PreferenceProfileDTO {
    func toDomain() -> UserPreferenceProfile {
        UserPreferenceProfile(
            genreScores: Dictionary(uniqueKeysWithValues: genreScores.map { (GenreId($0.key), $0.value) }),
            artistScores: Dictionary(uniqueKeysWithValues: artistScores.map { (ArtistId($0.key), $0.value) }),
            genreListeningScores: Dictionary(uniqueKeysWithValues: (genreListeningScores ?? [:]).map { (GenreId($0.key), $0.value) }),
            artistListeningScores: Dictionary(uniqueKeysWithValues: (artistListeningScores ?? [:]).map { (ArtistId($0.key), $0.value) }),
            timeBucketGenreScores: Dictionary(uniqueKeysWithValues: (timeBucketGenreScores ?? [:]).compactMap { key, scores in
                guard let bucket = Int(key) else { return nil }
                return (bucket, Dictionary(uniqueKeysWithValues: scores.map { (GenreId($0.key), $0.value) }))
            }),
            timeBucketSampleCounts: Dictionary(uniqueKeysWithValues: (timeBucketSampleCounts ?? [:]).compactMap { key, value in
                guard let bucket = Int(key) else { return nil }
                return (bucket, value)
            }),
            preferredFeatures: preferredFeatures.map {
                AudioFeatures(
                    energy: $0.energy,
                    danceability: $0.danceability,
                    valence: $0.valence,
                    tempo: $0.tempo
                )
            },
            explorationPreference: explorationPreference,
            positiveSignalCount: positiveSignalCount,
            negativeSignalCount: negativeSignalCount
        )
    }
}

extension AppSettings {
    func toDTO() -> AppSettingsDTO {
        AppSettingsDTO(
            historyMaxSize: historyConfig.maxSize,
            weights: RecommendationWeightsDTO(
                genre: recommendationWeights.genre,
                artist: recommendationWeights.artist,
                features: recommendationWeights.features,
                recentSignals: recommendationWeights.recentSignals,
                exploration: recommendationWeights.exploration,
                listening: recommendationWeights.listening,
                negativePenalty: recommendationWeights.negativePenalty,
                coldStart: recommendationWeights.coldStart
            )
        )
    }
}

extension AppSettingsDTO {
    func toDomain() -> AppSettings {
        AppSettings(
            historyConfig: HistoryConfig(maxSize: historyMaxSize),
            recommendationWeights: RecommendationWeights(
                genre: weights.genre,
                artist: weights.artist,
                features: weights.features,
                recentSignals: weights.recentSignals,
                exploration: weights.exploration,
                listening: weights.listening,
                negativePenalty: weights.negativePenalty,
                coldStart: weights.coldStart
            )
        )
    }
}
