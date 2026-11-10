package com.sensorial.app.ui.feed

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sensorial.domain.feed.FeedState
import com.sensorial.domain.gesture.RawGestureInput
import com.sensorial.domain.provider.MusicProvider
import com.sensorial.domain.provider.PlaybackEvent
import com.sensorial.domain.usecase.DiscoverySession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FeedViewModel(
    private val session: DiscoverySession,
    private val musicProvider: MusicProvider,
) : ViewModel(), DefaultLifecycleObserver {

    val state: StateFlow<FeedState> = session.state

    private val gestureMutex = Mutex()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        viewModelScope.launch { session.start() }
        musicProvider.playbackEvents()
            .onEach { event ->
                when (event) {
                    is PlaybackEvent.TrackActive -> session.syncToExternalTrack(event.trackId)
                    PlaybackEvent.TrackEnded -> {
                        // Serialize with gestures so natural advance cannot interleave mid-swipe.
                        gestureMutex.withLock { session.onTrackEnded() }
                    }
                    is PlaybackEvent.IsPaused -> session.onExternalPaused(event.paused)
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onStart(owner: LifecycleOwner) {
        onForeground()
    }

    fun onForeground() {
        viewModelScope.launch {
            musicProvider.reconcilePlayback()
        }
    }

    fun goToPrevious() = viewModelScope.launch { session.goBack() }

    fun goToNextInHistory() = viewModelScope.launch { session.advance() }

    fun togglePause() = viewModelScope.launch { session.togglePause() }

    fun onLike() = viewModelScope.launch { session.explicitLike() }

    fun onDislike() = viewModelScope.launch { session.explicitDislike() }

    fun onGestureCommitted(input: RawGestureInput) {
        viewModelScope.launch {
            // Serialize gesture handling so rapid swipes cannot interleave discover/play.
            gestureMutex.withLock {
                session.interpretAndHandle(input)
            }
        }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        super.onCleared()
    }
}

class FeedViewModelFactory(
    private val session: DiscoverySession,
    private val musicProvider: MusicProvider,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FeedViewModel::class.java))
        return FeedViewModel(session, musicProvider) as T
    }
}
