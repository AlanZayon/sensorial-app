import Foundation
import SensorialDomain

public actor CandidateQueryBuilder {
    public enum FetchProfile: Sendable {
        case interactive
        case prefetch
    }

    private let api: SpotifyWebApi
    private let targetPoolSize: Int
    private let diversity: DiversityConfig

    public init(
        api: SpotifyWebApi,
        targetPoolSize: Int = 70,
        diversity: DiversityConfig = DiversityConfig()
    ) {
        self.api = api
        self.targetPoolSize = targetPoolSize
        self.diversity = diversity
    }

    public func fetch(context: RecommendationContext) async throws -> [Track] {
        try await fetchDetailed(context: context, profile: .interactive).tracks
    }

    public func fetchDetailed(
        context: RecommendationContext,
        profile: FetchProfile? = nil
    ) async throws -> CandidatePool {
        let resolvedProfile = profile ?? (context.prefetch ? .prefetch : .interactive)
        var collected: [String: Track] = [:]
        var relatedIds = Set<ArtistId>()
        let excluded = context.excludedIds
        let blocked = RecentVersionBlock.fingerprints(
            recentTracks: context.recentTracks,
            currentTrack: context.currentTrack,
            config: diversity
        )

        func addAll(_ tracks: [Track]) {
            for track in tracks {
                if excluded.contains(track.id) { continue }
                let fingerprint = TrackFingerprint.of(track)
                if blocked.contains(fingerprint) { continue }
                if collected[fingerprint] != nil { continue }
                collected[fingerprint] = track
            }
        }

        func readyEnough(_ minSize: Int) -> Bool {
            guard collected.count >= minSize else { return false }
            return collected.values.contains(where: { $0.previewURL != nil }) || collected.count >= minSize + 10
        }

        let minReady = resolvedProfile == .interactive ? 28 : 48
        let relatedTopLimit: Int = {
            if context.discoveryMode == .explore {
                return resolvedProfile == .prefetch ? 8 : 5
            }
            if context.discoveryMode == .deepen { return 6 }
            if resolvedProfile == .interactive { return 3 }
            return 4
        }()
        let seedArtistLimit: Int = {
            if context.discoveryMode == .explore {
                return resolvedProfile == .prefetch ? 4 : 3
            }
            return resolvedProfile == .interactive ? 2 : 3
        }()
        let includeAlbums = resolvedProfile == .prefetch || context.discoveryMode == .deepen
        let sameArtistSearchLimit: Int = {
            switch context.discoveryMode {
            case .deepen: return 6
            case .explore: return 0
            case .normal: return 15
            }
        }()
        let baseGenres = context.seedGenres.isEmpty ? Self.defaultSeedGenres : context.seedGenres
        let genres: [GenreId] = {
            if context.discoveryMode == .explore {
                var merged = baseGenres
                for g in Self.exploreSeedGenres where !merged.contains(g) { merged.append(g) }
                return Array(merged.prefix(5))
            }
            return baseGenres
        }()
        let seedArtists = Array(context.topArtists.prefix(seedArtistLimit))
        let current = context.currentTrack
        let currentArtistId = current?.primaryArtist.id.value
        let eraQuery = Self.eraYearWindow(context: context)

        // Wave 1 — independent lookups in parallel.
        async let artistTop: [Track] = {
            guard let id = currentArtistId, !id.isEmpty else { return [] }
            let tops = (try? await api.artistTopTracks(artistId: id)) ?? []
            return context.discoveryMode == .deepen ? Array(tops.prefix(5)) : tops
        }()
        async let artistSearch: [Track] = {
            guard let name = current?.primaryArtist.name, sameArtistSearchLimit > 0 else { return [] }
            if context.discoveryMode == .explore && resolvedProfile == .interactive { return [] }
            return (try? await api.searchTracks(
                query: "artist:\"\(name)\"",
                limit: sameArtistSearchLimit
            )) ?? []
        }()
        async let currentGenreSearch: [Track] = {
            guard let g = current?.genres.first?.value else { return [] }
            if context.discoveryMode == .explore && resolvedProfile == .interactive { return [] }
            return (try? await api.searchTracks(query: "genre:\"\(g)\"", limit: 15)) ?? []
        }()
        async let related: [Artist] = {
            guard let id = currentArtistId, !id.isEmpty else { return [] }
            return (try? await api.relatedArtists(artistId: id)) ?? []
        }()

        let genreLimit = resolvedProfile == .interactive ? 12 : 15
        let genreResults = await withTaskGroup(of: [Track].self) { group in
            for genre in genres {
                group.addTask {
                    (try? await self.api.searchTracks(
                        query: "genre:\"\(genre.value)\"",
                        limit: genreLimit
                    )) ?? []
                }
            }
            var all: [Track] = []
            for await tracks in group { all.append(contentsOf: tracks) }
            return all
        }
        let seedTopResults = await withTaskGroup(of: [Track].self) { group in
            for artistId in seedArtists {
                group.addTask {
                    (try? await self.api.artistTopTracks(artistId: artistId.value)) ?? []
                }
            }
            var all: [Track] = []
            for await tracks in group { all.append(contentsOf: tracks) }
            return all
        }

        async let eraTracks: [Track] = {
            guard let eraQuery else { return [] }
            return (try? await api.searchTracks(query: eraQuery, limit: 12)) ?? []
        }()

        addAll(await artistTop)
        addAll(await artistSearch)
        addAll(await currentGenreSearch)
        addAll(genreResults)
        addAll(seedTopResults)
        addAll(await eraTracks)

        let relatedArtists = await related
        relatedArtists.forEach { relatedIds.insert($0.id) }

        // Wave 2 — related tops when still thin or prefetch/deepen/explore.
        if !readyEnough(minReady) || resolvedProfile == .prefetch ||
            context.discoveryMode == .deepen || context.discoveryMode == .explore {
            let relatedTops = await withTaskGroup(of: [Track].self) { group in
                for artist in relatedArtists.prefix(relatedTopLimit) {
                    group.addTask {
                        let tops = (try? await self.api.artistTopTracks(artistId: artist.id.value)) ?? []
                        return context.discoveryMode == .deepen ? Array(tops.prefix(4)) : tops
                    }
                }
                var all: [Track] = []
                for await tracks in group { all.append(contentsOf: tracks) }
                return all
            }
            addAll(relatedTops)
        }

        // Explore: second-order related graph.
        if context.discoveryMode == .explore &&
            (resolvedProfile == .prefetch || !readyEnough(minReady)) {
            let hubs = Array(relatedArtists.prefix(3))
            let second = await withTaskGroup(of: [Artist].self) { group in
                for artist in hubs {
                    group.addTask {
                        (try? await self.api.relatedArtists(artistId: artist.id.value)) ?? []
                    }
                }
                var all: [Artist] = []
                for await artists in group { all.append(contentsOf: artists) }
                return all
            }
            second.forEach { relatedIds.insert($0.id) }
            let secondTops = await withTaskGroup(of: [Track].self) { group in
                for artist in second.prefix(5) {
                    group.addTask {
                        let tops = (try? await self.api.artistTopTracks(artistId: artist.id.value)) ?? []
                        return Array(tops.prefix(3))
                    }
                }
                var all: [Track] = []
                for await tracks in group { all.append(contentsOf: tracks) }
                return all
            }
            addAll(secondTops)
        }

        // Wave 3 — albums only when needed (Android has artistRecentAlbumTracks; skip if unavailable).
        _ = includeAlbums

        // Wave 4 — fallbacks.
        if collected.isEmpty || collected.values.allSatisfy({ $0.previewURL == nil }) {
            let fallbacks = await withTaskGroup(of: [Track].self) { group in
                for query in Self.fallbackQueries {
                    group.addTask {
                        (try? await self.api.searchTracks(query: query, limit: 15)) ?? []
                    }
                }
                var all: [Track] = []
                for await tracks in group { all.append(contentsOf: tracks) }
                return all
            }
            addAll(fallbacks)
        }

        let tracks = Array(collected.values).sorted { lhs, rhs in
            let leftPreview = lhs.previewURL != nil
            let rightPreview = rhs.previewURL != nil
            if leftPreview != rightPreview { return leftPreview && !rightPreview }
            if collected.count >= minReady && lhs.featureConfidence != rhs.featureConfidence {
                return lhs.featureConfidence > rhs.featureConfidence
            }
            return lhs.popularity > rhs.popularity
        }
        .prefix(targetPoolSize)
        .map { $0 }

        return CandidatePool(tracks: tracks, relatedArtistIds: relatedIds)
    }

    private static func eraYearWindow(context: RecommendationContext) -> String? {
        let years = context.recentTracks.compactMap(\.releaseYear)
        if years.isEmpty {
            return context.discoveryMode == .explore ? "year:2015-2025" : nil
        }
        let mean = years.reduce(0, +) / years.count
        let span: Int
        switch context.discoveryMode {
        case .deepen: span = 4
        case .explore: span = 12
        case .normal: span = 7
        }
        let start = max(1950, mean - span)
        let end = min(2030, mean + span)
        return "year:\(start)-\(end)"
    }

    private static let defaultSeedGenres = [GenreId("pop"), GenreId("indie"), GenreId("electronic")]
    private static let exploreSeedGenres = [GenreId("alternative"), GenreId("experimental"), GenreId("world")]
    private static let fallbackQueries = ["year:2020-2025", "genre:\"indie\"", "genre:\"pop\""]
}
