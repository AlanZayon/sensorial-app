import Foundation

public struct CandidatePool: Sendable {
    public let tracks: [Track]
    public let relatedArtistIds: Set<ArtistId>

    public init(tracks: [Track], relatedArtistIds: Set<ArtistId> = []) {
        self.tracks = tracks
        self.relatedArtistIds = relatedArtistIds
    }
}
