# ADR 003 — Heuristic recommendation engine

## Status

Accepted (quality evolution: calibrated scoring + MMR + session transition + contextual bandit)

## Context

The product should demonstrate an explainable recommendation loop suitable for a portfolio. Navigation, discovery strategy, and explicit preference must stay separated. The engine should learn from listening behavior and time context, not only likes/dislikes. Spotify blocks `/v1/recommendations` for many third-party apps, so candidate quality must come from Search + artist graph (related artists / albums).

## Decision

- Expose `RecommendationEngine.recommend(profile, context, candidates)`.
- Ship `ScoredHeuristicRecommendationEngine` with **calibrated** linear weighted scores (signals clamped to signed/unit ranges) and expanded `ScoreBreakdown`:
  - explicit genre / artist affinity
  - listening-behavior affinity (no global session listening bias)
  - contextual time-bucket + weekend/weekday affinity (fallback when cold)
  - current-track similarity (boosted in Deepen, relaxed in Explore)
  - **transitionFit** over the last 3–5 played tracks (sequential session continuity)
  - session affinity (short-lived deepen/explore/skip streaks)
  - artist-graph proximity (same artist / related / profile seeds)
  - popularity and era fit
  - local feature cosine similarity (`TrackSimilarity`; swappable for embeddings)
  - recent interaction signals
  - exploration alignment driven by `DiscoveryMode`
  - recent artist / track / version penalties with artist-focus relief
  - soft negative preference penalty
  - weighted deepen-related / deepen-same-artist terms (no unweighted bonuses)
  - featureConfidence scales audio-feature terms when API falls back to heuristics
  - deterministic cold-start tie-break
  - **diversity** term filled by MMR selection
- Mode presets: `RecommendationWeightsPresets` (`Balanced` / `Focused` / `Discovery`).
- **MMR selection** (`MmrSelector`) reorders relevance scores; λ high in Deepen, low in Explore.
- **ContextualWeightPolicy** (ε-greedy + UCB hybrid) chooses weight presets from engagement reward; state persisted in `AppSettings.banditState` (Android) / session (+ settings DTO on iOS).
- **Duration-aware quick-skip** (`QuickSkip`) raises the progress threshold on short tracks via `quickSkipMinMs`.
- `RecommendationContext` carries `discoveryMode`, `recentTracks`, `topArtists`, `relatedArtistIds`, `sessionAffinity`, `blockedFingerprints`, optional `weightOverride`, and local time — never UI coordinates.
- Candidate pools via `CandidatePool` / `MusicProvider.getCandidatePool` with **mode-aware** arms:
  - profile top artists, related graph (+ Explore 2nd-order), seed/explore genres, era year window, album neighbors
  - prefetch uses the active `DiscoveryMode` (not always Normal)

## Consequences

- Ranking remains inspectable via `rankDetailed` (MMR slate + breakdown).
- Gestures map to semantic events; likes do not force track changes; unlike reverses like impact.
- Offline eval harness (`OfflineEvalHarnessTest` + `OfflineEvalMetrics`) guards mode divergence, remaster blocking, diversity@5, listening-proxy, cold/warm, deepen streak rate, and MMR vs relevance-only.
- Future remote embeddings replace `TrackSimilarity` / engine behind the same interfaces without rewriting the session.
- Android remains source of truth; iOS mirrors domain behavior (exclusions, presets, MMR, transitionFit, bandit, mode-aware retrieval).
