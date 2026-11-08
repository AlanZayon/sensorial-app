# Sensorial

Personal portfolio app for **gesture-driven music discovery**. Swipes are not only navigation — they express discovery intent and feed a heuristic recommendation engine. Playback and catalog come from officially allowed Spotify APIs (Phase 5); Phase 1 uses mocks.

## Platforms

| Platform | Stack | Status (Phase 1) |
|----------|--------|------------------|
| Android | Kotlin, Jetpack Compose, Coroutines/Flow | Buildable / testable on Windows |
| iOS | Swift, SwiftUI, Swift Concurrency | Source + tests ready; open on macOS with Xcode |

Native clients share the same domain rules, duplicated per platform (no Flutter / React Native / KMP).

## Repository layout

```text
.
├── android/          # Gradle multi-module app
│   ├── app/
│   └── core/
│       ├── domain/   # Pure Kotlin domain
│       ├── data/     # Mocks + in-memory repos
│       └── testing/
├── ios/              # SwiftUI app (open on Mac)
├── docs/ADR/         # Architecture decision records
└── README.md
```

## Phase 1 — Foundation

Implements:

- Domain models (`Track`, `PlaybackHistory`, `TrackInteraction`, `UserPreferenceProfile`)
- Gesture → intent interpretation (API + tests)
- Heuristic `RecommendationEngine` with configurable weights
- Mock `MusicProvider` and in-memory persistence

## Phase 2 — Gestures UI

Implements:

- Draggable track card with live intent overlays
- Edge-back + Anterior button for history (no preference conflict)
- Commit / cancel animations and track enter transitions
- Preference buttons removed from primary chrome (Play/Pause + Anterior remain)

## Phase 3 — Recommendation refinement

Implements:

- Gesture intensity / velocity / listening duration in profile updates and ranking
- Recent-signal boost via resolved interaction tracks (similar vs explore differentiated)
- Explainable `ScoreBreakdown` + cold-start tie-break
- Configured weights (`RecommendationWeights`, `SignalScoringConfig`)

## Phase 4 — Local persistence

Implements:

- Android Room (+ JSON) behind existing repository interfaces
- iOS SwiftData (+ Codable JSON) with the same boundaries
- Hydration on launch; resume history or seed mock catalog
- InMemory repos kept for domain tests

Does **not** include Spotify or cloud sync.

## Phase 5 — Spotify

Implements:

- OAuth Authorization Code + PKCE (`MusicProvider` swap; domain stays Spotify-free)
- Web API search / track / artist top-tracks for candidates; genre-heuristic audio features when `/v1/audio-features` is restricted
- Hybrid playback: Spotify App Remote when available, else 30s preview (ExoPlayer / AVPlayer)
- Auth gate UI; playback mode chip (`Spotify` vs `Preview 30s`)
- Mock provider remains the default when Client ID is unset (CI / local without secrets)

### Spotify Dashboard checklist

1. Create an app at [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard)
2. Redirect URIs (cadastre **as duas**):
   - `com.sensorial.app://callback` (Web API / PKCE)
   - `com.sensorial.app://app-remote` (App Remote / reprodução)
3. Android (obrigatório para App Remote): em **Settings → Android**:
   - Package name: `com.sensorial.app`
   - SHA1 debug (exemplo local): rode  
     `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android`  
     e cole o SHA1 (formato `AA:BB:...`)
4. iOS: bundle id `com.sensorial.app`
5. Copy the **Client ID** (no client secret for PKCE public clients)

Sem package+SHA1 corretos, o App Remote falha mesmo com Spotify Premium instalado; o app cai no preview (muitas faixas não têm `preview_url`).

### Android secrets (`android/local.properties`, gitignored)

```properties
spotify.clientId=YOUR_CLIENT_ID
spotify.useMock=false
```

Omit `spotify.clientId` or set `spotify.useMock=true` to keep `MockMusicProvider`.

See [docs/ADR/005-spotify-provider.md](docs/ADR/005-spotify-provider.md).

## Android quick start

Requirements: JDK 17+, Android SDK (API 35), Android Studio or command-line tools.

```bash
cd android
# Windows PowerShell example:
# $env:JAVA_HOME = "C:\path\to\jdk-17"
.\gradlew.bat :core:domain:test :core:data:test :app:assembleDebug
```

Application id: `com.sensorial.app`

Phase 5 builds with mock by default (no Client ID). Set `local.properties` as above for live Spotify.

## iOS quick start

Requires macOS + Xcode 15+ (iOS 17 deployment target). See [ios/README.md](ios/README.md).

## Core loop

```text
Current track → Gesture/intent → Interaction → Preference profile
    → Recommendation engine → Next track (history.push)
```

Navigation history is independent of preference signals. Going back never generates a new recommendation.

## License

Personal portfolio project. Respect Spotify Platform Rules when integrating APIs.
