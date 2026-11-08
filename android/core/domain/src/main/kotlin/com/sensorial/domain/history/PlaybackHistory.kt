package com.sensorial.domain.history

import com.sensorial.domain.model.DiscoveryMode
import com.sensorial.domain.model.Track

data class HistoryConfig(
    val maxSize: Int = DEFAULT_MAX_SIZE,
) {
    init {
        require(maxSize >= 1) { "maxSize must be at least 1" }
    }

    companion object {
        const val DEFAULT_MAX_SIZE: Int = 50
    }
}

/**
 * Browser-like navigation stack for discovery.
 * Independent from preference learning / recommendation.
 */
class PlaybackHistory(
    private val config: HistoryConfig = HistoryConfig(),
) {
    private val entries: MutableList<HistoryEntry> = mutableListOf()
    private var index: Int = -1

    val currentTrack: Track?
        get() = entries.getOrNull(index)?.track

    val currentEntry: HistoryEntry?
        get() = entries.getOrNull(index)

    val canGoBack: Boolean
        get() = index > 0

    val canGoForward: Boolean
        get() = index >= 0 && index < entries.lastIndex

    val size: Int
        get() = entries.size

    fun snapshot(): List<Track> = entries.map { it.track }

    fun entrySnapshot(): List<HistoryEntry> = entries.toList()

    fun currentIndex(): Int = index

    /**
     * Appends [track] as the new tip of history.
     * If the user had navigated backward, forward entries are discarded.
     */
    fun push(
        track: Track,
        discoveryMode: DiscoveryMode = DiscoveryMode.Normal,
        recommendedAtEpochMs: Long = 0L,
    ) {
        push(
            HistoryEntry(
                track = track,
                discoveryMode = discoveryMode,
                recommendedAtEpochMs = recommendedAtEpochMs,
            ),
        )
    }

    fun push(entry: HistoryEntry) {
        if (index < entries.lastIndex) {
            entries.subList(index + 1, entries.size).clear()
        }
        entries.add(entry)
        index = entries.lastIndex
        trimToMaxSize()
    }

    fun previous(): Track {
        check(canGoBack) { "Cannot go back" }
        index -= 1
        return entries[index].track
    }

    fun next(): Track {
        check(canGoForward) { "Cannot go forward" }
        index += 1
        return entries[index].track
    }

    fun updateCurrentListeningProgress(progress: Double) {
        val current = entries.getOrNull(index) ?: return
        entries[index] = current.copy(listeningProgress = progress.coerceIn(0.0, 1.0))
    }

    private fun trimToMaxSize() {
        while (entries.size > config.maxSize) {
            entries.removeAt(0)
            index -= 1
        }
        if (index < 0 && entries.isNotEmpty()) {
            index = 0
        }
    }
}
