package com.sensorial.app.ui.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun EdgeNavHints(
    canGoBack: Boolean,
    canGoForward: Boolean,
    modifier: Modifier = Modifier,
) {
    val backAlpha by animateFloatAsState(
        targetValue = if (canGoBack) 0.55f else 0f,
        label = "edgeBackHint",
    )
    val forwardAlpha by animateFloatAsState(
        targetValue = if (canGoForward) 0.55f else 0f,
        label = "edgeForwardHint",
    )

    Box(modifier = modifier.fillMaxSize()) {
        if (backAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(28.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.18f * backAlpha),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        if (forwardAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(28.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.18f * forwardAlpha),
                            ),
                        ),
                    ),
            )
        }
    }
}
