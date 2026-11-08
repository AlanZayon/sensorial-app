import Testing
@testable import SensorialDomain

@Suite("RecommendationEngine")
struct RecommendationEngineTests {
    private let engine = ScoredHeuristicRecommendationEngine()

    private func track(
        _ id: String,
        genres: [String],
        artist: String = "artist-\(id)",
        features: AudioFeatures = AudioFeatures(energy: 0.5, danceability: 0.5, valence: 0.5, tempo: 120)
    ) -> Track {
        Track(
            id: TrackId(id),
            title: id,
            artists: [Artist(id: ArtistId(artist), name: artist)],
            album: Album(id: "al-\(id)", name: "Album"),
            genres: genres.map(GenreId.init),
            audioFeatures: features
        )
    }

    private func interaction(
        trackId: String,
        vertical: VerticalIntent = .none,
        horizontal: HorizontalIntent = .none,
        intensity: GestureIntensity = .high,
        velocity: Double = 1,
        listeningMs: Int64 = 25_000,
        type: InteractionType = .preference,
        mode: DiscoveryMode = .normal,
        progress: Double = 0.8
    ) -> TrackInteraction {
        TrackInteraction(
            id: InteractionId("i-\(trackId)"),
            trackId: TrackId(trackId),
            interactionType: type,
            verticalIntent: vertical,
            horizontalIntent: horizontal,
            gestureIntensity: intensity,
            gestureVelocity: velocity,
            listeningDurationMs: listeningMs,
            timestampEpochMs: 1,
            listeningProgress: progress,
            discoveryMode: mode
        )
    }

    @Test("positive genre preference ranks matching track higher")
    func positiveGenre() {
        let profile = UserPreferenceProfile(
            genreScores: [GenreId("pop"): 0.9, GenreId("metal"): -0.8],
            positiveSignalCount: 1
        )
        let ranked = engine.rank(
            userProfile: profile,
            context: RecommendationContext(currentTrack: nil, recentInteractions: [], excludedIds: []),
            candidateTracks: [track("metal1", genres: ["metal"]), track("pop1", genres: ["pop"])]
        )
        #expect(ranked.first?.track.id.value == "pop1")
    }

    @Test("excluded ids are skipped")
    func excluded() {
        let result = engine.recommend(
            userProfile: UserPreferenceProfile(positiveSignalCount: 1),
            context: RecommendationContext(currentTrack: nil, recentInteractions: [], excludedIds: [TrackId("a")]),
            candidateTracks: [track("a", genres: ["pop"]), track("b", genres: ["pop"])]
        )
        #expect(result?.id.value == "b")
    }

    @Test("deepen mode prefers proximate track")
    func deepenMode() {
        let current = track(
            "cur",
            genres: ["electronic"],
            artist: "circuit",
            features: AudioFeatures(energy: 0.85, danceability: 0.9, valence: 0.5, tempo: 128)
        )
        let proximate = track(
            "near",
            genres: ["electronic"],
            artist: "circuit-b",
            features: AudioFeatures(energy: 0.82, danceability: 0.88, valence: 0.48, tempo: 126)
        )
        let novel = track(
            "far",
            genres: ["folk"],
            artist: "cedar",
            features: AudioFeatures(energy: 0.2, danceability: 0.25, valence: 0.6, tempo: 90)
        )
        let ranked = engine.rank(
            userProfile: UserPreferenceProfile(explorationPreference: 0.5, positiveSignalCount: 1),
            context: RecommendationContext(
                currentTrack: current,
                recentInteractions: [],
                excludedIds: [current.id],
                discoveryMode: .deepen
            ),
            candidateTracks: [novel, proximate]
        )
        #expect(ranked.first?.track.id.value == "near")
    }

    @Test("explore mode prefers novel track")
    func exploreMode() {
        let current = track("cur", genres: ["pop"], artist: "luna")
        let proximate = track("near", genres: ["pop"], artist: "luna")
        let novel = track("far", genres: ["ambient"], artist: "aeon")
        let ranked = engine.rank(
            userProfile: UserPreferenceProfile(explorationPreference: 0.5, positiveSignalCount: 1),
            context: RecommendationContext(
                currentTrack: current,
                recentInteractions: [],
                excludedIds: [current.id],
                discoveryMode: .explore
            ),
            candidateTracks: [proximate, novel]
        )
        #expect(ranked.first?.track.id.value == "far")
    }

    @Test("listening affinity elevates genre without explicit likes")
    func listeningAffinity() {
        let profile = UserPreferenceProfile(
            genreListeningScores: [GenreId("soul"): 0.9]
        )
        let ranked = engine.rank(
            userProfile: profile,
            context: RecommendationContext(currentTrack: nil, recentInteractions: [], excludedIds: []),
            candidateTracks: [track("rock1", genres: ["rock"]), track("soul1", genres: ["soul"])]
        )
        #expect(ranked.first?.track.id.value == "soul1")
    }

    @Test("breakdown total matches score")
    func breakdown() {
        let scored = engine.scoreDetailed(
            track: track("j1", genres: ["jazz"]),
            profile: UserPreferenceProfile(genreScores: [GenreId("jazz"): 0.4], positiveSignalCount: 1),
            context: RecommendationContext(currentTrack: nil, recentInteractions: [], excludedIds: [])
        )
        #expect(scored.score == scored.breakdown.total)
    }
}
