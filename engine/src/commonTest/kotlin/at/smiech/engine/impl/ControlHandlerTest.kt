package at.smiech.engine.impl

import at.smiech.engine.Direction
import at.smiech.engine.GameButton
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlHandlerTest {

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.001f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

    @Test
    fun `a held direction reads as full deflection and stops on release`() {
        val handler = ControlHandler()
        handler.onDirection(Direction.RIGHT, true)
        assertEquals(1f, handler.moveX)
        assertEquals(0f, handler.moveY)

        handler.onDirection(Direction.RIGHT, false)
        assertEquals(0f, handler.moveX)
    }

    @Test
    fun `up is negative and down positive, matching the framebuffer's y axis`() {
        val handler = ControlHandler()
        handler.onDirection(Direction.UP, true)
        assertEquals(-1f, handler.moveY)

        handler.onDirection(Direction.UP, false)
        handler.onDirection(Direction.DOWN, true)
        assertEquals(1f, handler.moveY)
    }

    @Test
    fun `opposite keys held together cancel out`() {
        val handler = ControlHandler()
        handler.onDirection(Direction.LEFT, true)
        handler.onDirection(Direction.RIGHT, true)
        assertEquals(0f, handler.moveX)

        // Releasing one leaves the other still held, rather than leaving nothing pressed.
        handler.onDirection(Direction.RIGHT, false)
        assertEquals(-1f, handler.moveX)
    }

    @Test
    fun `a key overrides the stick on its own axis but not on the other`() {
        val handler = ControlHandler()
        handler.onAxis(0.8f, 0.8f)
        handler.onDirection(Direction.LEFT, true)

        assertEquals(-1f, handler.moveX, "the key should win its own axis")
        assertTrue(handler.moveY > 0f, "the stick should keep the axis no key touched")
    }

    @Test
    fun `the stick's dead zone is rescaled rather than cut out`() {
        val handler = ControlHandler()

        handler.onAxis(ControlHandler.AXIS_DEAD_ZONE - 0.01f, 0f)
        assertEquals(0f, handler.moveX, "rest position should read as no input")

        // Just past the edge should be just off zero, not the jump to 0.2 that subtracting
        // nothing would give.
        handler.onAxis(ControlHandler.AXIS_DEAD_ZONE + 0.01f, 0f)
        assertTrue(handler.moveX > 0f && handler.moveX < 0.05f, "was ${handler.moveX}")

        handler.onAxis(1f, -1f)
        assertClose(1f, handler.moveX)
        assertClose(-1f, handler.moveY)
    }

    @Test
    fun `a press is reported once and only to the first reader`() {
        val handler = ControlHandler()
        handler.onButton(GameButton.PAUSE, true)

        assertTrue(handler.consumePress(GameButton.PAUSE))
        assertFalse(handler.consumePress(GameButton.PAUSE), "a press must not be read twice")
    }

    @Test
    fun `holding a key down does not repeat the press`() {
        val handler = ControlHandler()
        handler.onButton(GameButton.PAUSE, true)
        assertTrue(handler.consumePress(GameButton.PAUSE))

        // Android repeats ACTION_DOWN while a key is held; pause must not flicker because of it.
        repeat(5) { handler.onButton(GameButton.PAUSE, true) }
        assertFalse(handler.consumePress(GameButton.PAUSE))

        // Releasing and pressing again is a new press.
        handler.onButton(GameButton.PAUSE, false)
        handler.onButton(GameButton.PAUSE, true)
        assertTrue(handler.consumePress(GameButton.PAUSE))
    }

    @Test
    fun `a press shorter than a frame still registers`() {
        val handler = ControlHandler()
        handler.onButton(GameButton.CONFIRM, true)
        handler.onButton(GameButton.CONFIRM, false)
        assertTrue(handler.consumePress(GameButton.CONFIRM))
    }

    @Test
    fun `buttons do not bleed into one another`() {
        val handler = ControlHandler()
        handler.onButton(GameButton.PAUSE, true)

        assertFalse(handler.consumePress(GameButton.BACK))
        assertFalse(handler.consumePress(GameButton.CONFIRM))
        assertTrue(handler.consumePress(GameButton.PAUSE))
    }

    @Test
    fun `a gesture press has no release to wait for`() {
        val handler = ControlHandler()
        handler.onButtonPress(GameButton.BACK)
        assertTrue(handler.consumePress(GameButton.BACK))
        assertFalse(handler.consumePress(GameButton.BACK))
    }

    @Test
    fun `releaseAll drops everything held, including an unread press`() {
        val handler = ControlHandler()
        handler.onDirection(Direction.UP, true)
        handler.onAxis(1f, 1f)
        handler.onButton(GameButton.PAUSE, true)

        handler.releaseAll()

        assertEquals(0f, handler.moveX)
        assertEquals(0f, handler.moveY)
        assertFalse(
            handler.consumePress(GameButton.PAUSE),
            "a press nobody read before focus was lost should not fire later"
        )
    }

    @Test
    fun `a key pressed again after releaseAll works`() {
        val handler = ControlHandler()
        handler.onButton(GameButton.PAUSE, true)
        handler.releaseAll()

        // releaseAll clears the held flag too, so this counts as a fresh press rather than being
        // swallowed as a repeat.
        handler.onButton(GameButton.PAUSE, true)
        assertTrue(handler.consumePress(GameButton.PAUSE))
    }
}
