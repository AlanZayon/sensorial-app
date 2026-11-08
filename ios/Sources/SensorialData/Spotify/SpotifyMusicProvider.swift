import Foundation
import SensorialDomain

public final class SpotifyMusicProvider: MusicProvider, @unchecked Sendable {
    private let api: SpotifyWebApi
    private let candidates: CandidateQueryBuilder
    private let playback: HybridPlaybackController
    private let lock = NSLock()
    private var lastMode: PlaybackMode = .none

    public init(
        api: SpotifyWebApi,
        candidates: CandidateQueryBuilder,
        playback: HybridPlaybackController
    ) {
        self.api = api
        self.candidates = candidates
        self.playback = playback
    }

    public func searchTracks(query: String) async throws -> [Track] {
        let tracks = try await api.searchTracks(query: query)
        await MainActor.run { playback.rememberAll(tracks) }
        return tracks
    }

    public func getTrack(id: TrackId) async throws -> Track? {
        guard let track = try await api.getTrack(id: id) else { return nil }
        await MainActor.run { playback.remember(track) }
        return track
    }

    public func getCandidates(context: RecommendationContext) async throws -> [Track] {
        try await getCandidatePool(context: context).tracks
    }

    public func getCandidatePool(context: RecommendationContext) async throws -> CandidatePool {
        let pool = try await candidates.fetchDetailed(context: context)
        await MainActor.run { playback.rememberAll(pool.tracks) }
        return pool
    }

    public func playTrack(id: TrackId) async throws {
        try await playback.play(trackId: id.value)
        let mode = await MainActor.run { playback.mode }
        lock.lock()
        lastMode = mode
        lock.unlock()
    }

    public func playbackMode() -> PlaybackMode {
        lock.lock()
        defer { lock.unlock() }
        return lastMode
    }

    public func pausePlayback() async {
        await MainActor.run { playback.pause() }
    }

    public func resumePlayback() async {
        await MainActor.run { playback.resume() }
    }
}
