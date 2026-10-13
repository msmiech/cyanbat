package at.smiech.engine

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How [VirtualStick] turns a thumb into a deflection. */
class VirtualStickTest {

    private companion object {
        const val HOME_X = 60f
        const val HOME_Y = 300f
        const val RADIUS = 40f
    }

    /** Touches as a host reports them: the events once, and which pointers are down and where. */
    private class Touches : Input {
        private val queued = mutableListOf<Input.TouchEvent>()
        private val held = mutableSetOf<Int>()
        private val positions = mutableMapOf<Int, Pair<Int, Int>>()

        fun queue(
            type: Int,
            x: Int,
            y: Int,
            pointer: Int,
            down: Boolean = type != Input.TouchEvent.TOUCH_UP,
        ) {
            if (down) held += pointer else held -= pointer
            positions[pointer] = x to y
            queued += Input.TouchEvent().also {
                it.type = type
                it.x = x
                it.y = y
                it.pointer = pointer
            }
        }

        fun liftUnseen(pointer: Int) {
            held -= pointer
        }

        override val touchEvents: List<Input.TouchEvent>
            get() = queued.toList().also { queued.clear() }

        override fun isTouchDown(pointer: Int) = pointer in held
        override fun getTouchX(pointer: Int) = positions[pointer]?.first ?: 0
        override fun getTouchY(pointer: Int) = positions[pointer]?.second ?: 0
        override val pointerCount = 0
    }

    private class Harness {
        val touches = Touches()
        val stick = VirtualStick(HOME_X, HOME_Y, RADIUS, zoneRight = 200f, zoneTop = 150f)
            .apply { enabled = true }

        fun down(x: Int, y: Int, pointer: Int = 0) =
            touches.queue(Input.TouchEvent.TOUCH_DOWN, x, y, pointer)

        fun drag(x: Int, y: Int, pointer: Int = 0, down: Boolean = true) =
            touches.queue(Input.TouchEvent.TOUCH_DRAGGED, x, y, pointer, down)

        fun up(x: Int, y: Int, pointer: Int = 0) =
            touches.queue(Input.TouchEvent.TOUCH_UP, x, y, pointer)

        fun update() = stick.update(touches, touches.touchEvents)
    }

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

    @Test
    fun `a thumb put down on the rim pushes the stick all the way at once`() {
        val h = Harness()
        h.down((HOME_X + RADIUS).toInt(), HOME_Y.toInt())
        h.update()

        assertTrue(h.stick.held)
        assertClose(1f, h.stick.moveX)
        assertClose(0f, h.stick.moveY)
        assertClose(HOME_X, h.stick.centerX)
    }

    @Test
    fun `a thumb resting near the center does not steer`() {
        val h = Harness()
        h.down(HOME_X.toInt() + 3, HOME_Y.toInt() - 2)
        h.update()

        assertTrue(h.stick.held)
        assertEquals(0f, h.stick.moveX)
        assertEquals(0f, h.stick.moveY)
    }

    @Test
    fun `the deflection keeps the thumb's direction`() {
        val h = Harness()
        h.down(HOME_X.toInt(), HOME_Y.toInt())
        h.update()
        // Up and to the right at 45 degrees, past the rim.
        h.drag(HOME_X.toInt() + 50, HOME_Y.toInt() - 50)
        h.update()

        assertClose(1f, sqrt(h.stick.moveX * h.stick.moveX + h.stick.moveY * h.stick.moveY))
        assertClose(h.stick.moveX, -h.stick.moveY)
        assertTrue(h.stick.moveX > 0f)
    }

    @Test
    fun `half way out is slower than all the way`() {
        val h = Harness()
        h.down(HOME_X.toInt() + 20, HOME_Y.toInt())
        h.update()

        assertTrue(h.stick.moveX > 0.3f && h.stick.moveX < 0.6f, "was ${h.stick.moveX}")
    }

    @Test
    fun `the knob stays on the rim however far the thumb goes`() {
        val h = Harness()
        h.down(HOME_X.toInt(), HOME_Y.toInt())
        h.update()
        h.drag(HOME_X.toInt() + 120, HOME_Y.toInt())
        h.update()

        assertClose(HOME_X + RADIUS, h.stick.knobX)
        assertClose(HOME_Y, h.stick.knobY)
    }

    @Test
    fun `a thumb put down off the stick brings the stick to it at rest`() {
        val h = Harness()
        h.down(150, 200)
        h.update()

        assertTrue(h.stick.held)
        assertClose(150f, h.stick.centerX)
        assertClose(200f, h.stick.centerY)
        assertEquals(0f, h.stick.moveX)

        h.drag(150, 160)
        h.update()
        assertClose(-1f, h.stick.moveY)

        // Letting go sends it home.
        h.up(150, 160)
        h.update()
        assertFalse(h.stick.held)
        assertClose(HOME_X, h.stick.centerX)
        assertClose(HOME_Y, h.stick.centerY)
        assertEquals(0f, h.stick.moveY)
    }

    @Test
    fun `a thumb on the bar beside the frame still takes it`() {
        val h = Harness()
        h.down(-12, 330)
        h.update()
        assertTrue(h.stick.held)
    }

    @Test
    fun `a touch outside the zone is not the stick's`() {
        val h = Harness()
        h.down(400, 300)
        h.update()
        h.down(100, 100, pointer = 1)
        h.update()

        assertFalse(h.stick.held)
    }

    @Test
    fun `a thumb sliding into the zone takes it`() {
        val h = Harness()
        h.down(260, 300)
        h.update()
        assertFalse(h.stick.held)

        h.drag(190, 300)
        h.update()
        assertTrue(h.stick.held)
    }

    @Test
    fun `a mouse hovering over the stick does not take it`() {
        val h = Harness()
        h.drag(HOME_X.toInt() + 30, HOME_Y.toInt(), down = false)
        h.update()
        assertFalse(h.stick.held)
    }

    @Test
    fun `a second finger cannot take the stick from the first`() {
        val h = Harness()
        h.down(HOME_X.toInt() + RADIUS.toInt(), HOME_Y.toInt(), pointer = 0)
        h.update()
        h.down(HOME_X.toInt() - RADIUS.toInt(), HOME_Y.toInt(), pointer = 1)
        h.update()

        assertEquals(0, h.stick.pointer)
        assertClose(1f, h.stick.moveX)
    }

    @Test
    fun `a thumb that lifted while an overlay read its release lets go`() {
        val h = Harness()
        h.down(HOME_X.toInt() + RADIUS.toInt(), HOME_Y.toInt())
        h.update()

        h.touches.liftUnseen(0)
        h.update()
        assertFalse(h.stick.held)
        assertEquals(0f, h.stick.moveX)
    }

    @Test
    fun `a thumb held through an overlay steers from where it is now`() {
        val h = Harness()
        h.down(HOME_X.toInt(), HOME_Y.toInt())
        h.update()

        // The thumb moves while an overlay reads the events, and the stick sees none of them.
        h.drag(HOME_X.toInt(), HOME_Y.toInt() + RADIUS.toInt())
        h.touches.touchEvents
        h.update()
        assertClose(1f, h.stick.moveY)
    }

    @Test
    fun `switched off it lets go and reads nothing`() {
        val h = Harness()
        h.down(HOME_X.toInt() + RADIUS.toInt(), HOME_Y.toInt())
        h.update()

        h.stick.enabled = false
        assertFalse(h.stick.held)
        assertEquals(0f, h.stick.moveX)

        h.down(HOME_X.toInt() + RADIUS.toInt(), HOME_Y.toInt(), pointer = 1)
        h.update()
        assertFalse(h.stick.held)
    }
}
