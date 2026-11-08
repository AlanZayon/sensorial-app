package com.sensorial.app.ui.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sensorial.app.ui.theme.IntentColors

@Composable
fun IntentOverlay(
    preview: IntentPreview,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val targetAlpha = if (preview == IntentPreview.None) 0f else progress.coerceIn(0f, 1f)
    val alpha by animateFloatAsState(targetValue = targetAlpha, label = "intentOverlayAlpha")
    if (alpha <= 0.01f) return

    val tint = colorFor(preview)
    val alignment = alignmentFor(preview)
    val edgeBrush = edgeBrushFor(preview, tint, alpha)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha * 0.9f)
                .background(edgeBrush),
        )
        Text(
            text = preview.label(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(alignment)
                .padding(28.dp)
                .alpha(alpha)
                .border(width = 1.dp, color = tint.copy(alpha = 0.85f))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

private fun colorFor(preview: IntentPreview): Color = when (preview) {
    IntentPreview.None -> Color.Transparent
    IntentPreview.Deepen -> IntentColors.Deepen
    IntentPreview.Explore -> IntentColors.Explore
    IntentPreview.NavigateBack -> IntentColors.NavigateBack
    IntentPreview.NavigateForward -> IntentColors.NavigateForward
}

private fun alignmentFor(preview: IntentPreview): Alignment = when (preview) {
    IntentPreview.NavigateForward -> Alignment.TopCenter
    IntentPreview.NavigateBack -> Alignment.BottomCenter
    IntentPreview.Deepen -> Alignment.CenterStart
    IntentPreview.Explore -> Alignment.CenterEnd
    IntentPreview.None -> Alignment.Center
}

private fun edgeBrushFor(preview: IntentPreview, tint: Color, alpha: Float): Brush {
    val strong = tint.copy(alpha = 0.42f * alpha)
    val soft = Color.Transparent
    return when (preview) {
        IntentPreview.NavigateForward -> Brush.verticalGradient(listOf(strong, soft, soft))
        IntentPreview.NavigateBack -> Brush.verticalGradient(listOf(soft, soft, strong))
        IntentPreview.Deepen -> Brush.horizontalGradient(listOf(strong, soft, soft))
        IntentPreview.Explore -> Brush.horizontalGradient(listOf(soft, soft, strong))
        IntentPreview.None -> Brush.verticalGradient(listOf(soft, soft))
    }
}
