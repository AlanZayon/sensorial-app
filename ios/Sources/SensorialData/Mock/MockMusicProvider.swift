import Foundation
import SensorialDomain

public struct MockMusicProvider: MusicProvider {
    private let catalog: [Track]

    public init(catalog: [Track] = MockCatalog.tracks()) {
        self.catalog = catalog
    }

    public func searchTracks(query: String) async throws -> [Track] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return catalog }
        return catalog.filter {
            $0.title.lowercased().contains(q)
                || $0.artists.contains { $0.name.lowercased().contains(q) }
                || $0.genres.contains { $0.value.lowercased().contains(q) }
        }
    }

    public func getTrack(id: TrackId) async throws -> Track? {
        catalog.first { $0.id == id }
    }

    public func getCandidates(context: RecommendationContext) async throws -> [Track] {
        catalog
    }

    public func playTrack(id: TrackId) async throws {
        // Phase 1 fake playback hook.
    }
}
