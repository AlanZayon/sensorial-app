# ADR 005 — Spotify MusicProvider (auth, metadata, hybrid playback)

## Status

Accepted (Phase 5)

## Context

Phases 1–4 used `MockMusicProvider`. Sensorial must consume officially allowed Spotify APIs behind the existing `MusicProvider` boundary without leaking SDKs into domain. Spotify restricted `/v1/audio-features` and `/v1/recommendations` for many third-party apps (Nov 2024). Playback may use App Remote (Premium + Spotify app) or 30-second preview URLs.

## Decision

- Implement `SpotifyMusicProvider` in the data layer (Android + iOS mirrors).
- Authenticate with Authorization Code + PKCE (AppAuth on Android, `ASWebAuthenticationSession` on iOS).
- Redirect URI: `com.sensorial.app://callback`.
- Client ID via `android/local.properties` / iOS Info.plist or env (`SPOTIFY_CLIENT_ID`); never commit secrets.
- If Client ID is blank or `spotify.useMock=true` / `SPOTIFY_USE_MOCK`, keep `MockMusicProvider` (CI default).
- Candidates via Search + Artist top-tracks seeded by `RecommendationContext.currentTrack` and `seedGenres` (no Recommendations endpoint).
- Try `/v1/audio-features`; on failure use genre-heuristic `AudioFeatureFallback` so the heuristic engine still has `AudioFeatures`.
- Hybrid playback: App Remote first; ExoPlayer / AVPlayer preview fallback. Surface mode on `FeedState.playbackMode`.
- Domain remains free of Spotify types; `TrackId` stores the opaque Spotify track id.

## Consequences

- Real discovery requires a Dashboard app, redirect whitelist, and (for full tracks) Premium + Spotify installed.
- Preview mode is the portable path for Free accounts and emulator demos.
- Feature quality depends on genre heuristics when audio-features is unavailable.
- iOS App Remote needs the SpotifyiOS XCFramework linked in Xcode (stubbed until linked); Android vendors the App Remote AAR under `android/libs/`.
