package com.sensorial.app.ui.feed

import com.sensorial.domain.gesture.GestureInterpreter
import com.sensorial.domain.gesture.GestureInterpreterConfig
import com.sensorial.domain.gesture.RawGestureInput
import com.sensorial.domain.gesture.UserIntent
import com.sensorial.domain.model.DiscoveryMode
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Maps Compose pointer metrics to domain [RawGestureInput].
 * Compose Y grows downward; domain Y grows upward.
 */
object GestureMapper {

    fun toRawGestureInput(
        totalDragX: Float,
        totalDragY: Float,
        velocityX: Float,
        velocityY: Float,
        startX: Float,
        containerWidth: Float,
        containerHeight: Float = containerWidth,
    ): RawGestureInput {
        val width = containerWidth.coerceAtLeast(1f).toDouble()
        val height = containerHeight.coerceAtLeast(1f).toDouble()
        val speed = hypot(velocityX.toDouble(), velocityY.toDouble())
        return RawGestureInput(
            deltaX = totalDragX.toDouble(),
            deltaY = (-totalDragY).toDouble(),
            velocity = speed,
            startXNormalized = (startX / containerWidth.coerceAtLeast(1f)).toDouble().coerceIn(0.0, 1.0),
            screenWidth = width,
            screenHeight = height,
        )
    }

    fun previewIntent(
        totalDragX: Float,
        totalDragY: Float,
        startX: Float,
        containerWidth: Float,
        interpreter: GestureInterpreter = GestureInterpreter(),
        config: GestureInterpreterConfig = GestureInterpreterConfig(),
    ): Pair<IntentPreview, Float> {
        val distance = hypot(totalDragX.toDouble(), totalDragY.toDouble())
        val progress = (distance / config.minDistance).toFloat().coerceIn(0f, 1f)
        if (distance < config.minDistance * 0.55) {
            return IntentPreview.None to progress
        }

        val input = toRawGestureInput(
            totalDragX = totalDragX,
            totalDragY = totalDragY,
            velocityX = 600f,
            velocityY = 600f,
            startX = startX,
            containerWidth = containerWidth,
        )

        return when (val intent = interpreter.interpret(input)) {
            UserIntent.NavigateBack -> IntentPreview.NavigateBack to progress.coerceAtLeast(0.5f)
            UserIntent.NavigateForward -> IntentPreview.NavigateForward to progress.coerceAtLeast(0.5f)
            UserIntent.Invalid -> directionalHint(totalDragX, totalDragY) to progress
            is UserIntent.Discover -> when (intent.mode) {
                DiscoveryMode.Deepen -> IntentPreview.Deepen to progress.coerceAtLeast(0.55f)
                DiscoveryMode.Explore -> IntentPreview.Explore to progress.coerceAtLeast(0.55f)
                DiscoveryMode.Normal -> IntentPreview.NavigateForward to progress.coerceAtLeast(0.55f)
            }
        }
    }

    fun shouldCancelRelease(
        releaseDistance: Float,
        peakDistance: Float,
        totalDragX: Float,
        totalDragY: Float,
        velocityX: Float,
        velocityY: Float,
        commitDistance: Float,
        abandonFraction: Float = 0.55f,
    ): Boolean {
        if (releaseDistance < commitDistance) return true
        if (peakDistance > commitDistance && releaseDistance < peakDistance * abandonFraction) {
            return true
        }
        val towardOrigin = totalDragX * velocityX + totalDragY * velocityY < 0f
        val speed = hypot(velocityX.toDouble(), velocityY.toDouble()).toFloat()
        if (towardOrigin && speed > 400f && releaseDistance < peakDistance * 0.8f) {
            return true
        }
        return false
    }

    private fun directionalHint(totalDragX: Float, totalDragY: Float): IntentPreview {
        val absX = abs(totalDragX)
        val absY = abs(totalDragY)
        return when {
            absY >= absX && totalDragY < 0 -> IntentPreview.NavigateForward
            absY >= absX && totalDragY > 0 -> IntentPreview.NavigateBack
            absX > absY && totalDragX < 0 -> IntentPreview.Deepen
            absX > absY && totalDragX > 0 -> IntentPreview.Explore
            else -> IntentPreview.None
        }
    }
}
