package com.sensorial.app.ui.feed

import com.sensorial.domain.gesture.GestureInterpreter
import com.sensorial.domain.gesture.UserIntent
import com.sensorial.domain.model.DiscoveryMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GestureMapperTest {

    @Test
    fun `inverts compose Y axis for domain input`() {
        val input = GestureMapper.toRawGestureInput(
            totalDragX = 10f,
            totalDragY = 200f,
            velocityX = 0f,
            velocityY = 500f,
            startX = 200f,
            containerWidth = 400f,
        )
        assertEquals(10.0, input.deltaX)
        assertEquals(-200.0, input.deltaY)
        assertEquals(0.5, input.startXNormalized)
        assertEquals(500.0, input.velocity)
    }

    @Test
    fun `mapped upward swipe is navigate forward`() {
        val input = GestureMapper.toRawGestureInput(
            totalDragX = 5f,
            totalDragY = -220f,
            velocityX = 50f,
            velocityY = -800f,
            startX = 180f,
            containerWidth = 360f,
        )
        assertEquals(UserIntent.NavigateForward, GestureInterpreter().interpret(input))
    }

    @Test
    fun `mapped left swipe is deepen`() {
        val input = GestureMapper.toRawGestureInput(
            totalDragX = -250f,
            totalDragY = 10f,
            velocityX = -900f,
            velocityY = 20f,
            startX = 200f,
            containerWidth = 360f,
        )
        val intent = assertIs<UserIntent.Discover>(GestureInterpreter().interpret(input))
        assertEquals(DiscoveryMode.Deepen, intent.mode)
    }

    @Test
    fun `mapped right swipe is explore`() {
        val input = GestureMapper.toRawGestureInput(
            totalDragX = 250f,
            totalDragY = 10f,
            velocityX = 900f,
            velocityY = 20f,
            startX = 200f,
            containerWidth = 360f,
        )
        val intent = assertIs<UserIntent.Discover>(GestureInterpreter().interpret(input))
        assertEquals(DiscoveryMode.Explore, intent.mode)
    }

    @Test
    fun `preview reports proxima while dragging up`() {
        val (preview, progress) = GestureMapper.previewIntent(
            totalDragX = 0f,
            totalDragY = -160f,
            startX = 200f,
            containerWidth = 400f,
        )
        assertEquals(IntentPreview.NavigateForward, preview)
        assertTrue(progress > 0.5f)
    }

    @Test
    fun `cancel when release is below commit distance`() {
        assertTrue(
            GestureMapper.shouldCancelRelease(
                releaseDistance = 80f,
                peakDistance = 80f,
                totalDragX = 0f,
                totalDragY = -80f,
                velocityX = 0f,
                velocityY = -100f,
                commitDistance = 140f,
            ),
        )
    }
}
