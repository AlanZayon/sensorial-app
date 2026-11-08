import Testing
@testable import SensorialDomain

@Suite("PlaybackHistory")
struct PlaybackHistoryTests {
    private func track(_ id: String) -> Track {
        Track(
            id: TrackId(id),
            title: "Track \(id)",
            artists: [Artist(id: ArtistId("a1"), name: "Artist")],
            album: Album(id: "al", name: "Album"),
            genres: [GenreId("pop")],
            audioFeatures: AudioFeatures(energy: 0.5, danceability: 0.5, valence: 0.5, tempo: 120)
        )
    }

    @Test("push sets current track")
    func pushSetsCurrent() {
        let history = PlaybackHistory()
        history.push(track("A"))
        #expect(history.currentTrack?.id.value == "A")
        #expect(!history.canGoBack)
        #expect(!history.canGoForward)
    }

    @Test("previous and next navigate stack")
    func navigate() {
        let history = PlaybackHistory()
        history.push(track("A"))
        history.push(track("B"))
        history.push(track("C"))
        #expect(history.previous().id.value == "B")
        #expect(history.previous().id.value == "A")
        #expect(history.next().id.value == "B")
    }

    @Test("push after back truncates forward")
    func truncateForward() {
        let history = PlaybackHistory()
        history.push(track("A"))
        history.push(track("B"))
        history.push(track("C"))
        _ = history.previous()
        _ = history.previous()
        history.push(track("X"))
        #expect(history.snapshot().map(\.id.value) == ["A", "X"])
        #expect(!history.canGoForward)
    }

    @Test("respects max size")
    func maxSize() {
        let history = PlaybackHistory(config: HistoryConfig(maxSize: 3))
        history.push(track("A"))
        history.push(track("B"))
        history.push(track("C"))
        history.push(track("D"))
        #expect(history.size == 3)
        #expect(history.snapshot().map(\.id.value) == ["B", "C", "D"])
    }

    @Test("stores discovery mode metadata")
    func discoveryModeMeta() {
        let history = PlaybackHistory()
        history.push(track("A"), discoveryMode: .deepen, recommendedAtEpochMs: 42)
        #expect(history.currentEntry?.discoveryMode == .deepen)
        #expect(history.currentEntry?.recommendedAtEpochMs == 42)
    }
}
