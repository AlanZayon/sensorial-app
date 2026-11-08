import Foundation
import SensorialDomain

struct SpotifyTrackDTO: Decodable {
    var id: String
    var name: String
    var artists: [SpotifyArtistDTO]
    var album: SpotifyAlbumDTO?
    var duration_ms: Int64?
    var preview_url: String?
    var popularity: Int?
    var explicit: Bool?
}

struct SpotifyRelatedArtistsDTO: Decodable {
    var artists: [SpotifyArtistDTO]
}

struct SpotifyArtistDTO: Decodable {
    var id: String
    var name: String
    var genres: [String]?
}

struct SpotifyAlbumDTO: Decodable {
    var id: String
    var name: String
    var images: [SpotifyImageDTO]?
    var release_date: String?
}

struct SpotifyImageDTO: Decodable {
    var url: String
    var width: Int?
}

struct SpotifySearchDTO: Decodable {
    var tracks: SpotifyPagingDTO?
}

struct SpotifyPagingDTO: Decodable {
    var items: [SpotifyTrackDTO]
}

struct SpotifyTopTracksDTO: Decodable {
    var tracks: [SpotifyTrackDTO]
}

struct SpotifyAudioFeaturesDTO: Decodable {
    var id: String?
    var energy: Double?
    var danceability: Double?
    var valence: Double?
    var tempo: Double?
}

struct SpotifyAudioFeaturesListDTO: Decodable {
    var audio_features: [SpotifyAudioFeaturesDTO?]
}

public actor SpotifyWebApi {
    private let auth: SpotifyAuthRepository
    private var featureCache: [String: AudioFeatures] = [:]
    private var artistGenreCache: [String: [GenreId]] = [:]
    private var relatedArtistsCache: [String: [Artist]] = [:]

    public init(auth: SpotifyAuthRepository) {
        self.auth = auth
    }

    public func searchTracks(query: String, limit: Int = 20) async throws -> [Track] {
        guard !query.isEmpty else { return [] }
        var components = URLComponents(url: SpotifyConfig.apiBase.appendingPathComponent("search"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "type", value: "track"),
            URLQueryItem(name: "limit", value: String(min(50, max(1, limit)))),
            URLQueryItem(name: "market", value: "from_token"),
        ]
        let data = try await authorizedGet(components.url!)
        let decoded = try JSONDecoder().decode(SpotifySearchDTO.self, from: data)
        return try await mapTracks(decoded.tracks?.items ?? [])
    }

    public func getTrack(id: TrackId) async throws -> Track? {
        var components = URLComponents(
            url: SpotifyConfig.apiBase.appendingPathComponent("tracks/\(id.value)"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [URLQueryItem(name: "market", value: "from_token")]
        guard let data = try? await authorizedGet(components.url!) else { return nil }
        let dto = try JSONDecoder().decode(SpotifyTrackDTO.self, from: data)
        return try await mapTracks([dto]).first
    }

    /// Top-tracks requires a real ISO country code (not from_token).
    public func artistTopTracks(artistId: String, market: String = "BR") async throws -> [Track] {
        guard !artistId.isEmpty else { return [] }
        let url = SpotifyConfig.apiBase.appendingPathComponent("artists/\(artistId)/top-tracks")
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "market", value: market)]
        let data = try await authorizedGet(components.url!)
        let decoded = try JSONDecoder().decode(SpotifyTopTracksDTO.self, from: data)
        return try await mapTracks(decoded.tracks)
    }

    public func relatedArtists(artistId: String) async throws -> [Artist] {
        guard !artistId.isEmpty else { return [] }
        if let cached = relatedArtistsCache[artistId] { return cached }
        let url = SpotifyConfig.apiBase.appendingPathComponent("artists/\(artistId)/related-artists")
        guard let data = try? await authorizedGet(url) else { return [] }
        let decoded = try JSONDecoder().decode(SpotifyRelatedArtistsDTO.self, from: data)
        let artists = decoded.artists
            .filter { !$0.id.isEmpty && !$0.name.isEmpty }
            .map { Artist(id: ArtistId($0.id), name: $0.name) }
        relatedArtistsCache[artistId] = artists
        return artists
    }

    private func mapTracks(_ dtos: [SpotifyTrackDTO]) async throws -> [Track] {
        let artistIds = Array(Set(dtos.compactMap { $0.artists.first?.id }))
        var genresByArtist: [String: [GenreId]] = [:]
        for id in artistIds {
            genresByArtist[id] = try await artistGenres(id)
        }
        let features = try await audioFeatures(ids: dtos.map(\.id))
        return dtos.map { dto in
            let genres = dto.artists.first.flatMap { genresByArtist[$0.id] } ?? [GenreId("unknown")]
            let artwork = dto.album?.images?.sorted { ($0.width ?? 0) > ($1.width ?? 0) }.first?.url
            let artists = dto.artists
                .filter { !$0.id.isEmpty && !$0.name.isEmpty }
                .map { Artist(id: ArtistId($0.id), name: $0.name) }
            let safeArtists = artists.isEmpty ? [Artist(id: ArtistId("unknown"), name: "Unknown Artist")] : artists
            let apiFeatures = features[dto.id]
            let year = dto.album?.release_date.flatMap { Int($0.prefix(4)) }
            return Track(
                id: TrackId(dto.id),
                title: dto.name,
                artists: safeArtists,
                album: Album(
                    id: dto.album?.id ?? "album-\(dto.id)",
                    name: dto.album?.name ?? "Unknown Album",
                    artworkURL: artwork.flatMap(URL.init(string:)),
                    releaseYear: year
                ),
                genres: genres,
                audioFeatures: apiFeatures ?? AudioFeatureFallback.forGenres(genres),
                artworkURL: artwork.flatMap(URL.init(string:)),
                durationMs: dto.duration_ms ?? 180_000,
                previewURL: dto.preview_url.flatMap(URL.init(string:)),
                popularity: min(100, max(0, dto.popularity ?? 0)),
                featureConfidence: apiFeatures == nil ? 0.35 : 1,
                explicit: dto.explicit ?? false
            )
        }
    }

    private func artistGenres(_ artistId: String) async throws -> [GenreId] {
        if let cached = artistGenreCache[artistId] { return cached }
        let url = SpotifyConfig.apiBase.appendingPathComponent("artists/\(artistId)")
        guard let data = try? await authorizedGet(url) else { return [GenreId("unknown")] }
        let artist = try JSONDecoder().decode(SpotifyArtistDTO.self, from: data)
        let genres = (artist.genres ?? [])
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .map(GenreId.init)
        let resolved = genres.isEmpty ? [GenreId("unknown")] : genres
        artistGenreCache[artistId] = resolved
        return resolved
    }

    private func audioFeatures(ids: [String]) async throws -> [String: AudioFeatures] {
        let missing = ids.filter { featureCache[$0] == nil }
        for chunk in missing.chunked(into: 50) {
            var components = URLComponents(url: SpotifyConfig.apiBase.appendingPathComponent("audio-features"), resolvingAgainstBaseURL: false)!
            components.queryItems = [URLQueryItem(name: "ids", value: chunk.joined(separator: ","))]
            guard let data = try? await authorizedGet(components.url!) else { continue }
            guard let decoded = try? JSONDecoder().decode(SpotifyAudioFeaturesListDTO.self, from: data) else { continue }
            for dto in decoded.audio_features.compactMap({ $0 }) {
                guard
                    let id = dto.id,
                    let energy = dto.energy,
                    let dance = dto.danceability,
                    let valence = dto.valence,
                    let tempo = dto.tempo,
                    tempo > 0
                else { continue }
                featureCache[id] = AudioFeatures(
                    energy: min(1, max(0, energy)),
                    danceability: min(1, max(0, dance)),
                    valence: min(1, max(0, valence)),
                    tempo: tempo
                )
            }
        }
        return Dictionary(uniqueKeysWithValues: ids.compactMap { id in
            featureCache[id].map { (id, $0) }
        })
    }

    private func authorizedGet(_ url: URL) async throws -> Data {
        let token = try await MainActor.run { try await auth.validAccessToken() }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return data
    }
}

private extension Array {
    func chunked(into size: Int) -> [[Element]] {
        stride(from: 0, to: count, by: size).map { Array(self[$0..<Swift.min($0 + size, count)]) }
    }
}
