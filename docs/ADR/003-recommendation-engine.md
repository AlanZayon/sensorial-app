# ADR 003 — Heuristic recommendation engine

## Status

Accepted (quality evolution toward ~9.0–9.2)

## Context

The product should demonstrate an explainable recommendation loop suitable for a portfolio. Navigation, discovery strategy, and explicit preference must stay separated. The engine should learn from listening behavior and time context, not only likes/dislikes. Spotify blocks `/v1/recommendations` for many third-party apps, so candidate quality must come from Search + artist graph (related artists / albums).

## Decision

- Expose `RecommendationEngine.recommend(profile, context, candidates)`.
- Ship `ScoredHeuristicRecommendationEngine` with linear weighted scores and expanded `ScoreBreakdown`:
  - explicit genre / artist affinity
  - listening-behavior affinity (no global session listening bias)
  - contextual time-bucket + weekend/weekday affinity (fallback when cold)
  - current-track similarity (boosted in Deepen, relaxed in Explore)
  - session affinity (short-lived deepen/explore/skip streaks)
  - artist-graph proximity (same artist / related / profile seeds)
  - popularity and era fit
  - local feature cosine similarity (`TrackSimilarity`; swappable for embeddings)
  - recent interaction signals
  - exploration alignment driven by `DiscoveryMode`
  - recent artist / track / version penalties with artist-focus relief
  - soft negative preference penalty
  - featureConfidence scales audio-feature terms when API falls back to heuristics
  - deterministic cold-start tie-break
- Mode presets: `RecommendationWeightsPresets` (`Balanced` / `Focused` / `Discovery`).
- `RecommendationContext` carries `discoveryMode`, `recentTracks`, `topArtists`, `relatedArtistIds`, `sessionAffinity`, and local time — never UI coordinates.
- Candidate pools via `CandidatePool` / `MusicProvider.getCandidatePool` (mode-aware fetch).

## Consequences

- Ranking remains inspectable via `rankDetailed`.
- Gestures map to semantic events; likes do not force track changes; unlike reverses like impact.
- Offline eval harness (`OfflineEvalHarnessTest`) guards mode divergence, remaster blocking, and diversity.
- Future remote embeddings replace `TrackSimilarity` / engine behind the same interfaces without rewriting the session.
