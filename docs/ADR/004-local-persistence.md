# ADR 004 — Local persistence (Room / SwiftData)

## Status

Accepted (Phase 4)

## Context

Preference profile, interaction history, playback history, and settings must survive process death for a credible discovery loop. Domain rules must stay free of persistence frameworks.

## Decision

- Keep repository interfaces in Domain.
- Android: Room in `:core:data` (Android library) with JSON (`kotlinx.serialization`) for nested aggregates (`Track`, preference maps, weights).
- iOS: SwiftData models + repositories in `SensorialData`, same JSON/`Codable` approach for aggregates.
- Keep InMemory implementations for domain/unit tests.
- On launch: hydrate repositories, then `DiscoverySession.start()` resumes existing history or seeds the mock catalog.

## Consequences

- Cold start restores learning signals without a backend.
- Schema v1 uses destructive migration fallback; future versions need explicit migrations.
- App process still owns composition (`SensorialContainer`).
