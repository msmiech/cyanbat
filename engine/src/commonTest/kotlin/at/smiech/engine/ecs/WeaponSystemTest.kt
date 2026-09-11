package at.smiech.engine.ecs

import at.smiech.engine.math.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeaponSystemTest {

    private class Harness(interval: Float = 1f, alive: Boolean? = null) {
        val fired = mutableListOf<EntityId>()
        val world = World().apply { addSystem(WeaponSystem { fired += it }) }
        val shooter: EntityId = world.createEntity().also { id ->
            world.addComponent(id, TransformComponent(Rect.fromLTWH(0f, 0f, 10f, 10f)))
            world.addComponent(id, WeaponComponent(interval))
            if (alive != null) world.addComponent(id, HealthComponent(hitPoints = 1, alive = alive))
        }

        /** Advances in the game's own tick size, as the real loop does. */
        fun advance(seconds: Float, step: Float = 0.019f) {
            var elapsed = 0f
            while (elapsed < seconds) {
                world.update(step, null)
                elapsed += step
            }
        }
    }

    @Test
    fun `does not fire before the interval elapses`() {
        val h = Harness(interval = 1f)
        h.advance(0.9f)
        assertTrue(h.fired.isEmpty(), "fired early: ${h.fired.size} shots")
    }

    @Test
    fun `fires once the interval elapses`() {
        val h = Harness(interval = 1f)
        h.advance(1.05f)
        assertEquals(listOf(h.shooter), h.fired)
    }

    @Test
    fun `keeps firing on cadence`() {
        val h = Harness(interval = 1f)
        h.advance(5.05f)
        // Five seconds at a one second cadence: allow one either way for tick granularity.
        assertTrue(h.fired.size in 4..6, "expected ~5 shots, got ${h.fired.size}")
        assertTrue(h.fired.all { it == h.shooter })
    }

    /**
     * Subtracting the interval rather than zeroing the accumulator keeps the cadence from
     * drifting: the leftover time carries into the next shot.
     */
    @Test
    fun `cadence does not drift with an indivisible step`() {
        val h = Harness(interval = 0.1f)
        h.advance(1f, step = 0.03f)
        assertTrue(h.fired.size in 9..11, "expected ~10 shots, got ${h.fired.size}")
    }

    @Test
    fun `a dead shooter stops firing`() {
        val h = Harness(interval = 1f, alive = false)
        h.advance(3f)
        assertTrue(h.fired.isEmpty(), "a dead bat kept shooting")
    }

    @Test
    fun `a living shooter with health still fires`() {
        val h = Harness(interval = 1f, alive = true)
        h.advance(1.05f)
        assertEquals(1, h.fired.size)
    }

    @Test
    fun `entities without a weapon are ignored`() {
        val h = Harness(interval = 1f)
        val bystander = h.world.createEntity()
        h.world.addComponent(bystander, TransformComponent(Rect.fromLTWH(0f, 0f, 5f, 5f)))
        h.advance(1.05f)
        assertEquals(listOf(h.shooter), h.fired)
    }
}
