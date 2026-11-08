package com.sensorial.domain.model

/**
 * How the next track should be discovered — independent from navigation and explicit preference.
 */
enum class DiscoveryMode {
    /** Balanced next recommendation from profile + behavior + context. */
    Normal,

    /** Stay in the current musical territory (deepen). */
    Deepen,

    /** Seek novelty while still anchored to the global profile. */
    Explore,
}

enum class NavigationAction {
    Advance,
    Return,
    None,
}
