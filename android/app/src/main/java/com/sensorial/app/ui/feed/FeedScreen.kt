package com.sensorial.app.ui.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sensorial.app.ui.liked.LikedTracksEntryButton
import com.sensorial.domain.feed.FeedState
import com.sensorial.domain.feed.PlaybackMode
import com.sensorial.domain.feed.PlaybackStatus
import com.sensorial.domain.gesture.RawGestureInput

@Composable
fun FeedScreen(
    state: FeedState,
    onTogglePause: () -> Unit,
    onGestureCommitted: (RawGestureInput) -> Unit,
    onLike: () -> Unit = {},
    onOpenLiked: () -> Unit = {},
) {
    val context = LocalContext.current
    var showCoach by remember { mutableStateOf(!isGestureCoachSeen(context)) }
    val (likePulse, triggerLikePulse) = rememberLikePulse()
    val likeEnabled = state.currentTrack != null &&
        state.status != PlaybackStatus.Loading &&
        state.status != PlaybackStatus.ChangingTrack

    fun fireLike() {
        if (!likeEnabled) return
        if (!state.isCurrentTrackLiked) triggerLikePulse()
        onLike()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.status == PlaybackStatus.Loading && state.currentTrack == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            state.status == PlaybackStatus.Error -> {
                Text(
                    text = state.errorMessage ?: "Erro",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }
            state.status == PlaybackStatus.NoRecommendations && state.currentTrack == null -> {
                Text(
                    text = "Sem recomendações disponíveis",
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.currentTrack != null -> {
                val track = state.currentTrack!!
                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.97f)) togetherWith fadeOut()
                    },
                    contentKey = { it.id },
                    label = "trackTransition",
                    modifier = Modifier.fillMaxSize(),
                ) { animatedTrack ->
                    GestureFeedSurface(
                        track = animatedTrack,
                        paused = state.status.isPaused(),
                        // Block gestures while a commit/play is in flight — overlapping
                        // discovers were the main source of art/title vs audio desync.
                        enabled = state.status == PlaybackStatus.Playing ||
                            state.status == PlaybackStatus.Paused,
                        canGoBack = state.canGoBack,
                        canGoForward = state.canGoForward,
                        onTogglePause = onTogglePause,
                        onLike = ::fireLike,
                        onGestureCommitted = onGestureCommitted,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (state.status == PlaybackStatus.ChangingTrack) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                }
            }
            else -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        val hasImmersiveArt = state.currentTrack != null
        val brandColor = if (hasImmersiveArt) Color.White else MaterialTheme.colorScheme.onBackground
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(2f)
                .statusBarsPadding()
                .padding(start = 22.dp, top = 12.dp, end = 22.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showCoach = true },
                ),
        ) {
            Text(
                text = "Deriva",
                style = MaterialTheme.typography.headlineLarge,
                color = brandColor,
            )
            Text(
                text = feedSubtitle(state).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = brandColor.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        LikedTracksEntryButton(
            onClick = onOpenLiked,
            lightContent = hasImmersiveArt,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(2f)
                .statusBarsPadding()
                .padding(end = 16.dp, top = 10.dp),
        )

        TikTokLikeButton(
            enabled = likeEnabled,
            liked = state.isCurrentTrackLiked,
            pulseKey = likePulse,
            onLike = ::fireLike,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .zIndex(1f),
        )

        state.lastIntentLabel
            ?.takeIf { state.status != PlaybackStatus.ChangingTrack }
            ?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(2f)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                )
            }

        if (showCoach) {
            GestureCoachOverlay(
                onDismiss = { showCoach = false },
                modifier = Modifier.zIndex(4f),
            )
        }
    }
}

private fun playbackModeLabel(mode: PlaybackMode): String? = when (mode) {
    PlaybackMode.SpotifyRemote -> "Spotify"
    PlaybackMode.Preview -> "Preview 30s"
    PlaybackMode.None -> null
}

private fun feedSubtitle(state: FeedState): String {
    if (state.libraryPlayback) return "Curtidas"
    return playbackModeLabel(state.playbackMode) ?: "Gestos"
}
