package com.sensorial.app.ui.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val LikedPink = Color(0xFFFF2D55)

/**
 * TikTok-style like — heart only, flush to the right edge.
 */
@Composable
fun TikTokLikeButton(
    enabled: Boolean,
    liked: Boolean,
    pulseKey: Int,
    onLike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey <= 0) return@LaunchedEffect
        scale.snapTo(1f)
        scale.animateTo(1.28f, spring(dampingRatio = 0.38f, stiffness = 700f))
        scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 450f))
    }

    val heartTint = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        liked -> LikedPink
        else -> Color.White
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onLike,
            )
            .padding(end = 2.dp)
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = if (enabled) 1f else 0.4f
            },
    ) {
        Icon(
            imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (liked) "Remover curtida" else "Gostei",
            tint = heartTint,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
fun rememberLikePulse(): Pair<Int, () -> Unit> {
    var key by remember { mutableIntStateOf(0) }
    return key to { key += 1 }
}
