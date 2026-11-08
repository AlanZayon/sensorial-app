package com.sensorial.domain.model

enum class InteractionType {
    /** @deprecated Prefer ExplicitLike / ExplicitDislike / Discovery / Navigation*. */
    Preference,
    NavigationAdvance,
    NavigationReturn,
    Discovery,
    ExplicitLike,
    ExplicitDislike,
    /** Undo of [ExplicitLike] — reverses preference impact for accidental likes. */
    ExplicitUnlike,
}

enum class VerticalIntent {
    Positive,
    Negative,
    None,
}

enum class HorizontalIntent {
    /** @deprecated Use [DiscoveryMode.Deepen]. Kept for persistence compatibility. */
    Similar,
    Explore,
    None,
}

enum class GestureIntensity {
    Low,
    Medium,
    High,
}

/**
 * Domain event describing how the user reacted to a track.
 * Must not contain raw UI coordinates.
 */
data class TrackInteraction(
    val id: InteractionId,
    val trackId: TrackId,
    val interactionType: InteractionType,
    val verticalIntent: VerticalIntent = VerticalIntent.None,
    val horizontalIntent: HorizontalIntent = HorizontalIntent.None,
    val gestureIntensity: GestureIntensity = GestureIntensity.Medium,
    /** Semantic velocity in 0.0..1.0 */
    val gestureVelocity: Double = 0.5,
    val listeningDurationMs: Long,
    /** Epoch milliseconds. */
    val timestampEpochMs: Long,
    val artistId: ArtistId? = null,
    val trackDurationMs: Long? = null,
    /** 0..1 when duration is known; otherwise derived from listening vs target. */
    val listeningProgress: Double = 0.0,
    val discoveryMode: DiscoveryMode = DiscoveryMode.Normal,
    val localHour: Int = 12,
    val dayOfWeek: Int = 1,
    val navigationAction: NavigationAction = NavigationAction.None,
) {
    init {
        require(gestureVelocity in 0.0..1.0) { "gestureVelocity must be in 0..1" }
        require(listeningDurationMs >= 0) { "listeningDurationMs cannot be negative" }
        require(listeningProgress in 0.0..1.0) { "listeningProgress must be in 0..1" }
        require(localHour in 0..23) { "localHour must be in 0..23" }
        require(dayOfWeek in 1..7) { "dayOfWeek must be in 1..7 (ISO)" }
    }

    val effectiveListeningProgress: Double
        get() = when {
            listeningProgress > 0.0 -> listeningProgress
            trackDurationMs != null && trackDurationMs > 0L ->
                (listeningDurationMs.toDouble() / trackDurationMs.toDouble()).coerceIn(0.0, 1.0)
            else -> 0.0
        }
}

/** Four-hour time-of-day buckets for contextual preferences (0=00-03 … 5=20-23). */
fun timeBucketForHour(hour: Int): Int = (hour.coerceIn(0, 23) / 4).coerceIn(0, 5)
