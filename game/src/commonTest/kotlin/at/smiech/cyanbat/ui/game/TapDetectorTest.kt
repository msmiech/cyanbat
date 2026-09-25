package at.smiech.cyanbat.ui.game

import at.smiech.engine.Input.TouchEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TapDetectorTest {

    private fun event(type: Int, pointer: Int = 0, x: Int = 0, y: Int = 0) = TouchEvent().also {
        it.type = type
        it.pointer = pointer
        it.x = x
        it.y = y
    }

    private fun down(pointer: Int = 0) = event(TouchEvent.TOUCH_DOWN, pointer)
    private fun drag(pointer: Int = 0) = event(TouchEvent.TOUCH_DRAGGED, pointer)
    private fun up(pointer: Int = 0, x: Int = 0, y: Int = 0) =
        event(TouchEvent.TOUCH_UP, pointer, x, y)

    @Test
    fun `a press and release seen while listening is a tap`() {
        val taps = TapDetector()
        assertTrue(taps.taps(listOf(down())).isEmpty())

        val result = taps.taps(listOf(up(x = 40, y = 60)))
        assertEquals(1, result.size)
        assertEquals(40, result[0].x)
        assertEquals(60, result[0].y)
    }

    @Test
    fun `the lift of a finger already down when the overlay opened is not a tap`() {
        val taps = TapDetector()
        // Steering: the press happened during play, before the overlay was listening.
        taps.reset()
        assertTrue(taps.taps(listOf(drag(), drag())).isEmpty())
        assertTrue(taps.taps(listOf(up())).isEmpty())
    }

    @Test
    fun `reset forgets a press made before it`() {
        val taps = TapDetector()
        taps.taps(listOf(down()))
        taps.reset()
        assertTrue(taps.taps(listOf(up())).isEmpty())
    }

    @Test
    fun `a whole tap inside one frame counts`() {
        assertEquals(1, TapDetector().taps(listOf(down(), up())).size)
    }

    @Test
    fun `pointers are tracked separately`() {
        val taps = TapDetector()
        // Pointer 1 was already down; pointer 2 taps while it is held.
        taps.reset()
        val result = taps.taps(listOf(down(pointer = 2), up(pointer = 1), up(pointer = 2)))
        assertEquals(listOf(2), result.map { it.pointer })
    }

    @Test
    fun `a release is a tap only once`() {
        val taps = TapDetector()
        taps.taps(listOf(down()))
        assertEquals(1, taps.taps(listOf(up())).size)
        assertTrue(taps.taps(listOf(up())).isEmpty())
    }
}
