# ADR 001 — Layered native architecture

## Status

Accepted (Phase 1)

## Context

Sensorial needs equivalent business rules on iOS and Android while remaining a dual-native portfolio piece. Domain logic must stay free of UI frameworks and Spotify SDKs.

## Decision

- Organize each client as **Presentation → Domain → Data → Infrastructure**.
- Duplicate domain models and use cases in Swift and Kotlin (no KMP).
- Depend on protocols/interfaces (`MusicProvider`, repositories, `RecommendationEngine`).
- Keep Phase 1 persistence in-memory behind repository contracts.

## Consequences

- Two codebases to maintain for shared rules; ADRs and mirrored names reduce drift.
- Spotify and UI can be swapped without rewriting recommendation or history logic.
