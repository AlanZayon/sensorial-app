import Foundation

public struct TrackId: Hashable, Sendable, Codable {
    public let value: String
    public init(_ value: String) {
        precondition(!value.isEmpty)
        self.value = value
    }
}

public struct ArtistId: Hashable, Sendable, Codable {
    public let value: String
    public init(_ value: String) {
        precondition(!value.isEmpty)
        self.value = value
    }
}

public struct GenreId: Hashable, Sendable, Codable {
    public let value: String
    public init(_ value: String) {
        precondition(!value.isEmpty)
        self.value = value
    }
}

public struct InteractionId: Hashable, Sendable, Codable {
    public let value: String
    public init(_ value: String) {
        precondition(!value.isEmpty)
        self.value = value
    }
}

public struct Artist: Hashable, Sendable, Codable {
    public let id: ArtistId
    public let name: String
    public init(id: ArtistId, name: String) {
        self.id = id
        self.name = name
    }
}

public struct Album: Hashable, Sendable, Codable {
    public let id: String
    public let name: String
    public let artworkURL: URL?
    public let releaseYear: Int?
    public init(id: String, name: String, artworkURL: URL? = nil, releaseYear: Int? = nil) {
        self.id = id
        self.name = name
        self.artworkURL = artworkURL
        self.releaseYear = releaseYear
    }
}

public struct AudioFeatures: Hashable, Sendable, Codable {
    public let energy: Double
    public let danceability: Double
    public let valence: Double
    public let tempo: Double

    public init(energy: Double, danceability: Double, valence: Double, tempo: Double) {
        precondition((0...1).contains(energy))
        precondition((0...1).contains(danceability))
        precondition((0...1).contains(valence))
        precondition(tempo > 0)
        self.energy = energy
        self.danceability = danceability
        self.valence = valence
        self.tempo = tempo
    }
}

public struct Track: Hashable, Sendable, Codable, Identifiable {
    public let id: TrackId
    public let title: String
    public let artists: [Artist]
    public let album: Album
    public let genres: [GenreId]
    public let audioFeatures: AudioFeatures
    public let artworkURL: URL?
    public let durationMs: Int64
    /// 30s preview URL when App Remote is unavailable (Spotify Web API).
    public let previewURL: URL?
    public let popularity: Int
    public let featureConfidence: Double
    public let explicit: Bool

    public var primaryArtist: Artist { artists[0] }
    public var releaseYear: Int? { album.releaseYear }

    public init(
        id: TrackId,
        title: String,
        artists: [Artist],
        album: Album,
        genres: [GenreId],
        audioFeatures: AudioFeatures,
        artworkURL: URL? = nil,
        durationMs: Int64 = 180_000,
        previewURL: URL? = nil,
        popularity: Int = 0,
        featureConfidence: Double = 1,
        explicit: Bool = false
    ) {
        precondition(!title.isEmpty)
        precondition(!artists.isEmpty)
        precondition((0...1).contains(featureConfidence))
        precondition((0...100).contains(popularity))
        self.id = id
        self.title = title
        self.artists = artists
        self.album = album
        self.genres = genres
        self.audioFeatures = audioFeatures
        self.artworkURL = artworkURL ?? album.artworkURL
        self.durationMs = durationMs
        self.previewURL = previewURL
        self.popularity = popularity
        self.featureConfidence = featureConfidence
        self.explicit = explicit
    }
}
