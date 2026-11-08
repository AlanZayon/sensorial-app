import AVFoundation
import Foundation
import SensorialDomain

/// Prefer Spotify App Remote when available; otherwise AVPlayer preview (30s).
/// App Remote is wired via optional selector-based bridge so the package builds without the Spotify iOS SDK.
@MainActor
public final class HybridPlaybackController {
    public private(set) var mode: PlaybackMode = .none
    private var player: AVPlayer?
    private var trackCache: [String: Track] = [:]
    private let clientId: String
    private let redirectURI: String
    private var appRemote: AnyObject?

    public init(clientId: String, redirectURI: String) {
        self.clientId = clientId
        self.redirectURI = redirectURI
    }

    public func remember(_ track: Track) {
        trackCache[track.id.value] = track
    }

    public func rememberAll(_ tracks: [Track]) {
        tracks.forEach(remember)
    }

    public func play(trackId: String) async throws {
        if await tryPlayRemote(trackId: trackId) {
            player?.pause()
            mode = .spotifyRemote
            return
        }
        guard let preview = trackCache[trackId]?.previewURL else {
            mode = .none
            throw URLError(.resourceUnavailable)
        }
        let item = AVPlayerItem(url: preview)
        if player == nil {
            player = AVPlayer(playerItem: item)
        } else {
            player?.replaceCurrentItem(with: item)
        }
        player?.play()
        mode = .preview
    }

    public func pause() {
        switch mode {
        case .spotifyRemote:
            _ = appRemote?.perform(NSSelectorFromString("pause"))
        case .preview:
            player?.pause()
        case .none:
            break
        }
    }

    public func resume() {
        switch mode {
        case .spotifyRemote:
            _ = appRemote?.perform(NSSelectorFromString("resume"))
        case .preview:
            player?.play()
        case .none:
            break
        }
    }

    private func tryPlayRemote(trackId: String) async -> Bool {
        // Full App Remote requires SpotifyiOS XCFramework — documented in ios/README.
        // When the SDK is linked, replace this stub with SPTAppRemote connection.
        _ = clientId
        _ = redirectURI
        _ = trackId
        return false
    }
}
