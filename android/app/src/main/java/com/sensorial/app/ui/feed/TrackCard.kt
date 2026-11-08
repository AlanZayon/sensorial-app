package com.sensorial.app.ui.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sensorial.domain.feed.PlaybackStatus
import com.sensorial.domain.model.Track

@Composable
fun TrackCard(
    track: Track,
    paused: Boolean,
    modifier: Modifier = Modifier,
    overlay: @Composable () -> Unit = {},
) {
    val artworkUrl = track.artworkUrl?.takeIf { it.isNotBlank() }
        ?: track.album.artworkUrl?.takeIf { it.isNotBlank() }
    val genre = track.genres.firstOrNull()?.value
        ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
    val pauseGlyphAlpha by animateFloatAsState(
        targetValue = if (paused) 1f else 0f,
        label = "pauseGlyphAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = track.album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.28f),
                            0.45f to Color.Transparent,
                            0.72f to Color.Black.copy(alpha = 0.35f),
                            1.0f to Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
                ),
        )

        genre?.let { style ->
            Text(
                text = style.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 56.dp, end = 20.dp)
                    .background(Color.Black.copy(alpha = 0.38f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        // Clear of system navigation bar only (history is gesture-based).
        val contentBottomPad = 36.dp

        // Play affordance only while paused — centered, gone while playing.
        if (pauseGlyphAlpha > 0.01f) {
            Text(
                text = "▶",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White.copy(alpha = pauseGlyphAlpha * 0.92f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 72.dp, bottom = contentBottomPad, top = 28.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.primaryArtist.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = track.album.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        overlay()
    }
}

fun PlaybackStatus.isPaused(): Boolean = this == PlaybackStatus.Paused
