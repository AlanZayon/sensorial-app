import Foundation

public protocol MusicProvider: Sendable {
    func searchTracks(query: String) async throws -> [Track]
    func getTrack(id: TrackId) async throws -> Track?
    func getCandidates(context: RecommendationContext) async throws -> [Track]
    func getCandidatePool(context: RecommendationContext) async throws -> CandidatePool
    func playTrack(id: TrackId) async throws
    func playbackMode() -> PlaybackMode
    func pausePlayback() async
    func resumePlayback() async
}

public extension MusicProvider {
    func playbackMode() -> PlaybackMode { .none }
    func pausePlayback() async {}
    func resumePlayback() async {}
    func getCandidatePool(context: RecommendationContext) async throws -> CandidatePool {
        CandidatePool(tracks: try await getCandidates(context: context))
    }
}
