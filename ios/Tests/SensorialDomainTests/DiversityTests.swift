import Testing
@testable import SensorialDomain

@Suite("Diversity")
struct DiversityTests {
    private func track(_ id: String, artist: String) -> Track {
        Track(
            id: TrackId(id),
            title: id,
            artists: [Artist(id: ArtistId(artist), name: artist)],
            album: Album(id: "al-\(id)", name: "Album"),
            genres: [GenreId("pop")],
            audioFeatures: AudioFeatures(energy: 0.5, danceability: 0.5, valence: 0.5, tempo: 120)
        )
    }

    @Test("recent artist penalty rises with repeats")
    func artistPenalty() {
        let recent = (0..<5).map { track("t\($0)", artist: "same") }
        let candidate = track("new", artist: "same")
        let other = track("other", artist: "other")
        let samePenalty = RecentArtistPenalty.score(candidate: candidate, recentTracks: recent)
        let otherPenalty = RecentArtistPenalty.score(candidate: other, recentTracks: recent)
        #expect(samePenalty > otherPenalty)
    }

    @Test("track repetition penalty hits known id")
    func trackPenalty() {
        let recent = [track("a", artist: "x"), track("b", artist: "y"), track("a", artist: "x")]
        let hit = RecentTrackPenalty.score(candidate: track("a", artist: "x"), recentTracks: recent)
        let miss = RecentTrackPenalty.score(candidate: track("z", artist: "z"), recentTracks: recent)
        #expect(hit > 0)
        #expect(miss == 0)
    }

    @Test("artist focus needs multi-signal")
    func focusMultiSignal() {
        let focusTrack = track("f1", artist: "focus")
        let interactions = [
            TrackInteraction(
                id: InteractionId("1"),
                trackId: focusTrack.id,
                interactionType: .navigationReturn,
                listeningDurationMs: 40_000,
                timestampEpochMs: 1,
                artistId: ArtistId("focus"),
                listeningProgress: 0.8
            ),
            TrackInteraction(
                id: InteractionId("2"),
                trackId: focusTrack.id,
                interactionType: .explicitLike,
                listeningDurationMs: 30_000,
                timestampEpochMs: 2,
                artistId: ArtistId("focus"),
                listeningProgress: 0.7
            ),
        ]
        let result = ArtistFocusDetector.detect(
            recentInteractions: interactions,
            interactionTracks: [focusTrack.id: focusTrack]
        )
        #expect(result.focusedArtistId == ArtistId("focus"))

        let skipOnly = [
            TrackInteraction(
                id: InteractionId("s"),
                trackId: TrackId("other"),
                interactionType: .navigationAdvance,
                listeningDurationMs: 1_000,
                timestampEpochMs: 1,
                artistId: ArtistId("skip"),
                listeningProgress: 0.05
            ),
        ]
        let noFocus = ArtistFocusDetector.detect(
            recentInteractions: skipOnly,
            interactionTracks: [TrackId("other"): track("other", artist: "skip")]
        )
        #expect(noFocus.focusedArtistId == nil)
    }
}
