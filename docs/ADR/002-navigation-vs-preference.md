# ADR 002 — Navigation history vs preference signals

## Status

Accepted (Phase 1)

## Context

Gesture feeds often conflate “next item” with “I like / dislike this.” Sensorial must let users go back to previous tracks without implying a new recommendation or losing history because of a preference swipe.

## Decision

- **`PlaybackHistory`** owns stack navigation (`previous`, `next`, `push`, limits). Going back/forward never calls the recommendation engine.
- **`TrackInteraction` + `UserPreferenceProfile`** own preference learning.
- Preference confirmation advances discovery via `push` of a newly recommended track and truncates any forward history (browser-like).
- Edge-back / explicit previous controls are navigation intents, separate from horizontal “explore / similar” preference intents.

## Consequences

- UI must map physical gestures to either navigation or preference intents, never both in one confirmed action.
- Returning to a past track can later emit a separate `navigationReturn` signal without mutating the stack semantics.
