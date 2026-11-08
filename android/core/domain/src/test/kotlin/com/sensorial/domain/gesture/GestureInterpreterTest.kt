package com.sensorial.domain.gesture

import com.sensorial.domain.model.DiscoveryMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GestureInterpreterTest {
    private val interpreter = GestureInterpreter()

    @Test
    fun `swipe up is navigate forward`() {
        val intent = interpreter.interpret(raw(deltaX = 0.0, deltaY = 200.0))
        assertEquals(UserIntent.NavigateForward, intent)
    }

    @Test
    fun `swipe down is navigate back`() {
        val intent = interpreter.interpret(raw(deltaX = 0.0, deltaY = -200.0))
        assertEquals(UserIntent.NavigateBack, intent)
    }

    @Test
    fun `swipe left is deepen`() {
        val intent = interpreter.interpret(raw(deltaX = -220.0, deltaY = 10.0))
        val discover = assertIs<UserIntent.Discover>(intent)
        assertEquals(DiscoveryMode.Deepen, discover.mode)
    }

    @Test
    fun `swipe right is explore`() {
        val intent = interpreter.interpret(raw(deltaX = 220.0, deltaY = 10.0))
        val discover = assertIs<UserIntent.Discover>(intent)
        assertEquals(DiscoveryMode.Explore, discover.mode)
    }

    @Test
    fun `short swipe is invalid`() {
        assertEquals(UserIntent.Invalid, interpreter.interpret(raw(deltaX = 20.0, deltaY = 20.0)))
    }

    @Test
    fun `diagonal without dominance is invalid`() {
        assertEquals(UserIntent.Invalid, interpreter.interpret(raw(deltaX = 160.0, deltaY = 150.0)))
    }

    private fun raw(deltaX: Double, deltaY: Double) = RawGestureInput(
        deltaX = deltaX,
        deltaY = deltaY,
        velocity = 800.0,
        startXNormalized = 0.5,
    )
}
