package at.smiech.engine.ecs

import at.smiech.engine.Input
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerInputSystemTest {

    private companion object {
        const val WIDTH = 480
        const val HEIGHT = 320
        const val TICK = 0.019f
        const val BAT_WIDTH = 45f
        const val BAT_HEIGHT = 40f
    }

    /** Queues touch events and hands them over exactly once, as the real handler does. */
    private class FakeInput : Input {
        private val queued = mutableListOf<Input.TouchEvent>()

        fun queue(type: Int, x: Int, y: Int, pointer: Int) {
            queued += Input.TouchEvent().also {
                it.type = type
                it.x = x
                it.y = y
                it.pointer = pointer
            }
        }

        override val touchEvents: List<Input.TouchEvent>
            get() = queued.toList().also { queued.clear() }

        override fun isTouchDown(pointer: Int) = false
        override fun getTouchX(pointer: Int) = 0
        override fun getTouchY(pointer: Int) = 0
        override val accelX = 0f
        override val accelY = 0f
        override val accelZ = 0f
        override val pointerCount = 0
    }

    private class Harness(batX: Float = 100f, batY: Float = 100f) {
        val input = FakeInput()
        val world = World().apply {
            addSystem(PlayerInputSystem(WIDTH, HEIGHT))
            addSystem(MovementSystem())
        }
        val bat: EntityId = world.createEntity().also { id ->
            world.addComponent(
                id,
                TransformComponent(Rect.fromLTWH(batX, batY, BAT_WIDTH, BAT_HEIGHT))
            )
            world.addComponent(id, VelocityComponent(Vector2.Zero))
            world.addComponent(id, HealthComponent(lives = 3))
            world.addComponent(id, PlayerControlComponent())
        }

        val rect: Rect get() = world.getComponent(bat, TransformComponent::class)!!.rect
        val control: PlayerControlComponent
            get() = world.getComponent(bat, PlayerControlComponent::class)!!

        fun tick(times: Int = 1) = repeat(times) { world.update(TICK, input) }

        fun down(x: Int, y: Int, pointer: Int = 0) =
            input.queue(Input.TouchEvent.TOUCH_DOWN, x, y, pointer)

        fun drag(x: Int, y: Int, pointer: Int = 0) =
            input.queue(Input.TouchEvent.TOUCH_DRAGGED, x, y, pointer)

        fun up(x: Int, y: Int, pointer: Int = 0) =
            input.queue(Input.TouchEvent.TOUCH_UP, x, y, pointer)

        fun kill() {
            world.getComponent(bat, HealthComponent::class)!!.alive = false
        }
    }

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

    @Test
    fun `a grab on the sprite drags it one to one, keeping the offset`() {
        val h = Harness()
        // Down near the bat's top-left corner, well away from its centre.
        h.down(110, 110)
        h.tick()
        assertTrue(h.control.dragging)

        h.drag(160, 140)
        h.tick()

        // The finger moved +50/+30 and the bat moved exactly with it: the spot it was caught by is
        // still under the fingertip, rather than the sprite snapping its centre there.
        assertClose(150f, h.rect.left)
        assertClose(130f, h.rect.top)
    }

    @Test
    fun `a grab just outside the sprite still counts, further out does not`() {
        val bat = Rect.fromLTWH(100f, 100f, BAT_WIDTH, BAT_HEIGHT)

        val justOutside = Harness()
        justOutside.down(
            (bat.left - PlayerInputSystem.GRAB_PADDING + 1f).toInt(),
            bat.centerY.toInt()
        )
        justOutside.tick()
        assertTrue(justOutside.control.dragging, "a touch inside the padded box should grab")

        val wellOutside = Harness()
        wellOutside.down(
            (bat.left - PlayerInputSystem.GRAB_PADDING - 10f).toInt(),
            bat.centerY.toInt()
        )
        wellOutside.tick()
        assertFalse(wellOutside.control.dragging, "a touch beyond the padded box should not grab")
    }

    @Test
    fun `a touch away from the bat flies it over at the catch-up speed, then drags it`() {
        val h = Harness()
        h.down(400, 300)
        h.tick()
        assertFalse(h.control.dragging, "should still be travelling")

        val travelled = sqrt(
            (h.rect.centerX - 122.5f) * (h.rect.centerX - 122.5f) +
                (h.rect.centerY - 120f) * (h.rect.centerY - 120f)
        )
        assertClose(PlayerInputSystem.CATCH_UP_SPEED * TICK, travelled, 0.5f)

        // Comfortably longer than crossing the framebuffer takes at that speed.
        h.tick(times = 120)
        assertTrue(h.control.dragging, "should have arrived and locked on")
        assertClose(400f, h.rect.centerX, 1f)
        assertClose(300f, h.rect.centerY, 1f)
    }

    @Test
    fun `the bat holds still while the finger does`() {
        val h = Harness()
        h.down(110, 110)
        h.tick()
        val settled = h.rect

        h.tick(times = 10)
        assertEquals(settled, h.rect)
    }

    @Test
    fun `the bat keeps closing on a held touch across silent ticks`() {
        val h = Harness()
        h.down(400, 200)
        h.tick()
        val afterFirstTick = h.rect.centerX

        // No further events. Touch events are consumed once per frame while the world ticks
        // several times, so the pointer's position has to outlive the event that reported it.
        h.tick(times = 5)
        assertTrue(h.rect.centerX > afterFirstTick, "a held touch should keep pulling the bat")
    }

    @Test
    fun `the first finger keeps control while a second one drags`() {
        val h = Harness()
        h.down(110, 110, pointer = 0)
        h.tick()
        val grabbed = h.rect

        h.drag(400, 300, pointer = 1)
        h.tick()
        assertEquals(grabbed, h.rect, "a second finger must not steer")

        // Once the first lifts, the second may take over.
        h.up(110, 110, pointer = 0)
        h.tick()
        assertEquals(PlayerControlComponent.NO_POINTER, h.control.activePointer)

        h.drag(400, 300, pointer = 1)
        h.tick()
        assertEquals(1, h.control.activePointer)
    }

    @Test
    fun `lifting the finger stops the bat`() {
        val h = Harness()
        h.down(400, 300)
        h.tick(times = 3)
        val stoppedAt = h.rect

        h.up(400, 300)
        h.tick(times = 10)
        assertEquals(stoppedAt, h.rect, "the bat should not coast on to the last touch after release")
    }

    @Test
    fun `the bat stays inside the framebuffer`() {
        val h = Harness()
        h.down(110, 110)
        h.tick()

        h.drag(-500, -500)
        h.tick(times = 200)
        assertClose(0f, h.rect.left)
        assertClose(0f, h.rect.top)

        h.drag(2000, 2000)
        h.tick(times = 200)
        assertClose(WIDTH.toFloat(), h.rect.right)
        assertClose(HEIGHT.toFloat(), h.rect.bottom)
    }

    @Test
    fun `an edge does not block the other axis`() {
        val h = Harness(batX = 0f, batY = 100f)
        h.down(20, 120)
        h.tick()

        // Pushing left into the wall while moving down: the vertical half must still land.
        h.drag(-100, 200)
        h.tick(times = 60)
        assertClose(0f, h.rect.left)
        assertTrue(h.rect.top > 100f, "vertical movement should survive the horizontal clamp")
    }

    @Test
    fun `a mouse that never goes down still steers`() {
        val h = Harness()
        // Desktop hosts report motion without a press, so a drag is the first event ever seen.
        h.drag(300, 200)
        h.tick(times = 200)
        assertClose(300f, h.rect.centerX, 1f)
        assertClose(200f, h.rect.centerY, 1f)
    }

    @Test
    fun `a dead bat drops and ignores input`() {
        val h = Harness()
        h.down(110, 110)
        h.tick()
        h.kill()

        val before = h.rect
        h.drag(400, 300)
        h.tick()
        assertEquals(before.left, h.rect.left)
        assertClose(before.top + 2f, h.rect.top)
        assertEquals(PlayerControlComponent.NO_POINTER, h.control.activePointer)
    }

    @Test
    fun `the hit cooldown still counts down`() {
        val h = Harness()
        h.control.hitCooldown = 0.5f
        h.tick(times = 10)
        assertClose(0.5f - 10 * TICK, h.control.hitCooldown, 0.001f)
    }
}
