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
        /** Where the tests put a thumb down, which is where the stick then is. */
        const val X = 300
        const val Y = 200
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
        val stick = VirtualStick(RADIUS).apply { enabled = true }

        fun down(x: Int = X, y: Int = Y, pointer: Int = 0) =
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
    fun `there is no stick until a thumb is down and then it is centered under it at rest`() {
        val h = Harness()
        h.update()
        assertFalse(h.stick.held)

        h.down()
        h.update()
        assertTrue(h.stick.held)
        assertClose(X.toFloat(), h.stick.centerX)
        assertClose(Y.toFloat(), h.stick.centerY)
        assertEquals(0f, h.stick.moveX)
        assertEquals(0f, h.stick.moveY)
    }

    @Test
    fun `wherever on the screen the thumb goes down is the center`() {
        for ((x, y) in listOf(5 to 5, 620 to 40, 320 to 350, -12 to 330)) {
            val h = Harness()
            h.down(x, y)
            h.update()
            assertTrue(h.stick.held, "a thumb at ($x, $y) did not take the stick")
            assertClose(x.toFloat(), h.stick.centerX)
            assertClose(y.toFloat(), h.stick.centerY)
        }
    }

    @Test
    fun `pushed to the rim it is all the way`() {
        val h = Harness()
        h.down()
        h.update()
        h.drag(X + RADIUS.toInt(), Y)
        h.update()

        assertClose(1f, h.stick.moveX)
        assertClose(0f, h.stick.moveY)
    }

    @Test
    fun `a thumb wandering near the center does not steer`() {
        val h = Harness()
        h.down()
        h.update()
        h.drag(X + 3, Y - 2)
        h.update()

        assertEquals(0f, h.stick.moveX)
        assertEquals(0f, h.stick.moveY)
    }

    @Test
    fun `the deflection keeps the thumb's direction`() {
        val h = Harness()
        h.down()
        h.update()
        // Up and to the right at 45 degrees, past the rim.
        h.drag(X + 50, Y - 50)
        h.update()

        assertClose(1f, sqrt(h.stick.moveX * h.stick.moveX + h.stick.moveY * h.stick.moveY))
        assertClose(h.stick.moveX, -h.stick.moveY)
        assertTrue(h.stick.moveX > 0f)
    }

    @Test
    fun `half way out is slower than all the way`() {
        val h = Harness()
        h.down()
        h.update()
        h.drag(X + 20, Y)
        h.update()

        assertTrue(h.stick.moveX > 0.3f && h.stick.moveX < 0.6f, "was ${h.stick.moveX}")
    }

    /** The sensitivity: a wider stick needs the thumb to go further for the same speed. */
    @Test
    fun `a wider stick is less sensitive`() {
        val narrow = Harness()
        val wide = Harness().apply { stick.radius = RADIUS * 2 }
        for (h in listOf(narrow, wide)) {
            h.down()
            h.update()
            h.drag(X + 30, Y)
            h.update()
        }

        // Three quarters of the way out on the one, three eighths on the other.
        assertTrue(narrow.stick.moveX > 0.75f, "was ${narrow.stick.moveX}")
        assertTrue(wide.stick.moveX < narrow.stick.moveX / 2f, "was ${wide.stick.moveX}")
    }

    @Test
    fun `the knob stays on the rim however far the thumb goes`() {
        val h = Harness()
        h.down()
        h.update()
        h.drag(X + 120, Y)
        h.update()

        assertClose(X + RADIUS, h.stick.knobX)
        assertClose(Y.toFloat(), h.stick.knobY)
    }

    @Test
    fun `letting go takes the stick away and the next thumb puts it down afresh`() {
        val h = Harness()
        h.down()
        h.update()
        h.drag(X, Y - 40)
        h.update()
        assertClose(-1f, h.stick.moveY)

        h.up(X, Y - 40)
        h.update()
        assertFalse(h.stick.held)
        assertEquals(0f, h.stick.moveY)

        h.down(100, 300, pointer = 1)
        h.update()
        assertClose(100f, h.stick.centerX)
        assertClose(300f, h.stick.centerY)
        assertEquals(0f, h.stick.moveY)
    }

    /** One the stick missed going down, because an overlay read the touch, takes it as it moves. */
    @Test
    fun `a finger already down takes the stick when it moves`() {
        val h = Harness()
        h.down()
        h.touches.touchEvents
        h.update()
        assertFalse(h.stick.held)

        h.drag(X + 10, Y)
        h.update()
        assertTrue(h.stick.held)
        assertClose(X + 10f, h.stick.centerX)
        assertEquals(0f, h.stick.moveX)
    }

    @Test
    fun `a mouse hovering does not take it`() {
        val h = Harness()
        h.drag(X, Y, down = false)
        h.update()
        assertFalse(h.stick.held)
    }

    @Test
    fun `a second finger cannot take the stick from the first`() {
        val h = Harness()
        h.down(pointer = 0)
        h.update()
        h.drag(X + RADIUS.toInt(), Y, pointer = 0)
        h.down(X - 100, Y, pointer = 1)
        h.update()

        assertEquals(0, h.stick.pointer)
        assertClose(X.toFloat(), h.stick.centerX)
        assertClose(1f, h.stick.moveX)
    }

    @Test
    fun `a thumb that lifted while an overlay read its release lets go`() {
        val h = Harness()
        h.down()
        h.update()
        h.drag(X + RADIUS.toInt(), Y)
        h.update()

        h.touches.liftUnseen(0)
        h.update()
        assertFalse(h.stick.held)
        assertEquals(0f, h.stick.moveX)
    }

    @Test
    fun `a thumb held through an overlay steers from where it is now`() {
        val h = Harness()
        h.down()
        h.update()

        // The thumb moves while an overlay reads the events, and the stick sees none of them.
        h.drag(X, Y + RADIUS.toInt())
        h.touches.touchEvents
        h.update()
        assertClose(1f, h.stick.moveY)
    }

    @Test
    fun `switched off it lets go and reads nothing`() {
        val h = Harness()
        h.down()
        h.update()
        h.drag(X + RADIUS.toInt(), Y)
        h.update()

        h.stick.enabled = false
        assertFalse(h.stick.held)
        assertEquals(0f, h.stick.moveX)

        h.down(pointer = 1)
        h.update()
        assertFalse(h.stick.held)
    }
}
