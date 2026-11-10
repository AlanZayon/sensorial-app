package com.sensorial.app.ui.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.input.pointer.positionChange
import com.sensorial.domain.gesture.GestureInterpreter
import com.sensorial.domain.gesture.GestureInterpreterConfig
import com.sensorial.domain.gesture.RawGestureInput
import com.sensorial.domain.gesture.UserIntent
import com.sensorial.domain.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

private const val DoubleTapWindowMs = 280L

private enum class DragAxis {
    Undecided,
    Horizontal,
    Vertical,
}

/** Project drag onto a single axis so the card does not follow diagonal finger motion. */
private fun axisLockedOffset(total: Offset, axis: DragAxis): Offset = when (axis) {
    DragAxis.Horizontal -> Offset(total.x, 0f)
    DragAxis.Vertical -> Offset(0f, total.y)
    DragAxis.Undecided -> Offset.Zero
}

private fun resolveDragAxis(total: Offset): DragAxis =
    if (abs(total.x) >= abs(total.y)) DragAxis.Horizontal else DragAxis.Vertical

@Composable
fun GestureFeedSurface(
    track: Track,
    paused: Boolean,
    enabled: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onTogglePause: () -> Unit,
    onLike: () -> Unit,
    onGestureCommitted: (RawGestureInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var preview by remember { mutableStateOf(IntentPreview.None) }
    var progress by remember { mutableFloatStateOf(0f) }
    var startX by remember { mutableFloatStateOf(0f) }
    var totalDrag by remember { mutableStateOf(Offset.Zero) }
    val velocityTracker = remember { VelocityTracker() }
    var lastTapUptimeMs by remember { mutableLongStateOf(0L) }
    var pendingPauseJob by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    // Only a clean tap (never crossed this slop) toggles pause — any drag+release never pauses.
    val dragSlopPx = with(density) { 10.dp.toPx() }
    // ~120dp of travel required to commit; abandoning mid-swipe snaps back.
    val commitDistancePx = with(density) { 120.dp.toPx() }
    val interpreter = remember(commitDistancePx) {
        GestureInterpreter(
            GestureInterpreterConfig(minDistance = commitDistancePx.toDouble()),
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val exitDistance = with(density) { 420.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    translationY = offsetY.value
                    rotationZ = (offsetX.value / widthPx.coerceAtLeast(1f)) * 12f
                    alpha = 1f - (abs(offsetX.value) + abs(offsetY.value)) / (exitDistance * 2.2f)
                }
                .pointerInput(track.id, enabled, widthPx, heightPx, commitDistancePx) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        startX = down.position.x
                        totalDrag = Offset.Zero
                        var exceededDragSlop = false
                        var peakDistance = 0f
                        var lockedAxis = DragAxis.Undecided
                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        val pointerId = down.id
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()
                                ?: break
                            if (change.pressed) {
                                val delta = change.positionChange()
                                change.consume()
                                totalDrag += delta
                                val distance = hypot(totalDrag.x.toDouble(), totalDrag.y.toDouble()).toFloat()
                                if (distance > dragSlopPx) {
                                    exceededDragSlop = true
                                    if (lockedAxis == DragAxis.Undecided) {
                                        lockedAxis = resolveDragAxis(totalDrag)
                                    }
                                }
                                val locked = axisLockedOffset(totalDrag, lockedAxis)
                                val lockedDistance = hypot(locked.x.toDouble(), locked.y.toDouble()).toFloat()
                                peakDistance = max(peakDistance, lockedDistance)
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                if (exceededDragSlop) {
                                    scope.launch {
                                        offsetX.snapTo(locked.x)
                                        offsetY.snapTo(locked.y)
                                    }
                                    val (nextPreview, nextProgress) = GestureMapper.previewIntent(
                                        totalDragX = locked.x,
                                        totalDragY = locked.y,
                                        startX = startX,
                                        containerWidth = widthPx,
                                        interpreter = interpreter,
                                        config = GestureInterpreterConfig(
                                            minDistance = commitDistancePx.toDouble(),
                                        ),
                                    )
                                    preview = nextPreview
                                    progress = nextProgress
                                }
                            } else {
                                released = true
                                change.consume()
                            }
                        }

                        if (!exceededDragSlop) {
                            scope.launch {
                                preview = IntentPreview.None
                                progress = 0f
                                offsetX.snapTo(0f)
                                offsetY.snapTo(0f)
                                totalDrag = Offset.Zero
                            }
                            val now = down.uptimeMillis
                            val isDoubleTap = lastTapUptimeMs > 0L &&
                                now - lastTapUptimeMs <= DoubleTapWindowMs
                            if (isDoubleTap) {
                                pendingPauseJob?.cancel()
                                pendingPauseJob = null
                                lastTapUptimeMs = 0L
                                onLike()
                            } else {
                                lastTapUptimeMs = now
                                pendingPauseJob?.cancel()
                                pendingPauseJob = scope.launch {
                                    delay(DoubleTapWindowMs)
                                    if (lastTapUptimeMs == now) {
                                        onTogglePause()
                                        lastTapUptimeMs = 0L
                                    }
                                }
                            }
                            return@awaitEachGesture
                        }

                        val velocity = velocityTracker.calculateVelocity()
                        val axis = if (lockedAxis == DragAxis.Undecided) {
                            resolveDragAxis(totalDrag)
                        } else {
                            lockedAxis
                        }
                        val locked = axisLockedOffset(totalDrag, axis)
                        val lockedVelocityX = if (axis == DragAxis.Horizontal) velocity.x else 0f
                        val lockedVelocityY = if (axis == DragAxis.Vertical) velocity.y else 0f
                        val releaseDistance = hypot(locked.x.toDouble(), locked.y.toDouble()).toFloat()
                        val abandoned = GestureMapper.shouldCancelRelease(
                            releaseDistance = releaseDistance,
                            peakDistance = peakDistance,
                            totalDragX = locked.x,
                            totalDragY = locked.y,
                            velocityX = lockedVelocityX,
                            velocityY = lockedVelocityY,
                            commitDistance = commitDistancePx,
                        )
                        val input = GestureMapper.toRawGestureInput(
                            totalDragX = locked.x,
                            totalDragY = locked.y,
                            velocityX = lockedVelocityX,
                            velocityY = lockedVelocityY,
                            startX = startX,
                            containerWidth = widthPx,
                            containerHeight = heightPx,
                        )
                        val interpreted = interpreter.interpret(input)
                        val shouldCommit = !abandoned && interpreted !is UserIntent.Invalid
                        scope.launch {
                            if (shouldCommit) {
                                val targetX = when (axis) {
                                    DragAxis.Horizontal -> if (locked.x >= 0f) exitDistance else -exitDistance
                                    else -> 0f
                                }
                                val targetY = when (axis) {
                                    DragAxis.Vertical -> if (locked.y >= 0f) exitDistance else -exitDistance
                                    else -> 0f
                                }
                                launch { offsetX.animateTo(targetX, tween(240)) }
                                launch { offsetY.animateTo(targetY, tween(240)) }
                                delay(220)
                                onGestureCommitted(input)
                                preview = IntentPreview.None
                                progress = 0f
                                offsetX.snapTo(0f)
                                offsetY.snapTo(0f)
                                totalDrag = Offset.Zero
                            } else {
                                preview = IntentPreview.None
                                progress = 0f
                                launch {
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                                launch {
                                    offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                                totalDrag = Offset.Zero
                            }
                        }
                    }
                },
        ) {
            TrackCard(track = track, paused = paused) {
                IntentOverlay(preview = preview, progress = progress)
            }
            EdgeNavHints(canGoBack = canGoBack, canGoForward = canGoForward)
        }
    }
}
