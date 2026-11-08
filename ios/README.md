# Sensorial — iOS

Swift / SwiftUI client mirroring the Android domain rules. Phase 1 sources are ready; **build and test on macOS with Xcode 15+** (deployment target iOS 17).

## Layout

```text
ios/
├── Package.swift                 # Domain + Data packages (swift test)
├── Sources/
│   ├── SensorialDomain/
│   └── SensorialData/
├── Tests/
│   └── SensorialDomainTests/
├── SensorialApp/                 # SwiftUI application target sources
└── README.md
```

## Open on a Mac

### Option A — Swift Package (domain tests)

```bash
cd ios
swift test
```

### Option B — Xcode app

1. Open Xcode → **File → New → Project → App**
2. Product Name: `Sensorial`, Organization Identifier: `com.sensorial`, Interface: SwiftUI, Language: Swift
3. Set bundle id to `com.sensorial.app`, minimum deployment iOS 17
4. Replace generated sources with files under `SensorialApp/`
5. **File → Add Package Dependencies… → Add Local…** → select this `ios/` folder
6. Link `SensorialDomain` and `SensorialData` to the app target
7. Run on simulator

## Phase 2–4 notes

- Primary interaction is drag on the track card (`GestureFeedSurface`)
- Preference intents: up / down / left / right overlays
- Navigate back: left-edge drag rightward, plus “← Anterior” button
- Play/Pause remains in chrome; preference buttons removed from primary UI
- **SwiftData** persists history, interactions, preference profile, and settings
- App launch hydrates SwiftData then resumes history or seeds the mock catalog
- InMemory repositories remain available for package unit tests

## Phase 5 — Spotify

- PKCE via `ASWebAuthenticationSession`; tokens in Keychain
- `SpotifyMusicProvider` behind `MusicProvider` when `SPOTIFY_CLIENT_ID` is set
- Preview playback via `AVPlayer`; App Remote requires linking SpotifyiOS XCFramework in Xcode (stub returns unavailable → preview)
- Auth gate before the gesture feed when Spotify is enabled

### Secrets

Add to the app Info.plist (or Xcode build settings / xcconfig, not committed):

| Key | Value |
|-----|--------|
| `SPOTIFY_CLIENT_ID` | Dashboard Client ID |
| `SPOTIFY_USE_MOCK` | `YES` to force mock |

URL type / callback scheme: `com.sensorial.app` (path `callback` → `com.sensorial.app://callback`).

Without Client ID, the app keeps `MockMusicProvider` (same as Android CI default).
