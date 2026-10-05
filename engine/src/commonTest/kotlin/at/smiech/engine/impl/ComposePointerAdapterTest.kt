package at.smiech.engine.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import at.smiech.engine.Input.TouchEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposePointerAdapterTest {

    /** A view the framebuffer's own size, so a view pixel is a frame pixel. */
    private val fit = FrameFit(640, 360, left = 0, top = 0, width = 640, height = 360)

    private val touches = PointerTouchHandler()

    private fun feed(pressed: Boolean, previousPressed: Boolean, consumed: Boolean = false) {
        val change = PointerInputChange(
            id = PointerId(7),
            uptimeMillis = 0,
            position = Offset(40f, 60f),
            pressed = pressed,
            previousUptimeMillis = 0,
            previousPosition = Offset(40f, 60f),
            previousPressed = previousPressed,
            isInitiallyConsumed = consumed,
        )
        touches.onComposePointerEvent(PointerEvent(listOf(change)), fit)
    }

    @Test
    fun `a finger lifting is a plain release`() {
        feed(pressed = true, previousPressed = false)
        feed(pressed = false, previousPressed = true)

        val events = touches.touchEvents
        assertEquals(listOf(TouchEvent.TOUCH_DOWN, TouchEvent.TOUCH_UP), events.map { it.type })
        assertFalse(events[1].canceled)
    }

    /**
     * What Compose makes of Android canceling a touch - its back gesture taking over a swipe that
     * began on the game: a release that arrives already consumed.
     */
    @Test
    fun `a touch the system takes over ends in a canceled release`() {
        feed(pressed = true, previousPressed = false)
        feed(pressed = false, previousPressed = true, consumed = true)

        val release = touches.touchEvents.last()
        assertEquals(TouchEvent.TOUCH_UP, release.type)
        assertTrue(release.canceled)
        assertFalse(touches.isTouchDown(release.pointer), "the canceled pointer is still held")
    }

    /** The events are pooled, so a cancel would otherwise come back on a later, ordinary release. */
    @Test
    fun `a recycled event forgets it was a cancel`() {
        feed(pressed = true, previousPressed = false)
        feed(pressed = false, previousPressed = true, consumed = true)
        touches.touchEvents
        feed(pressed = true, previousPressed = false)
        feed(pressed = false, previousPressed = true)
        // This read hands the first batch, the cancel among it, back to the pool the next events
        // are drawn from.
        touches.touchEvents

        feed(pressed = true, previousPressed = false)
        feed(pressed = false, previousPressed = true)
        assertTrue(touches.touchEvents.none { it.canceled })
    }
}
