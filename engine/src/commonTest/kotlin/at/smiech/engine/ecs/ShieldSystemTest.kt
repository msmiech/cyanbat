package at.smiech.engine.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShieldSystemTest {

    private val world = World().apply { addSystem(ShieldSystem()) }

    private fun shielded(shield: ShieldComponent, alive: Boolean = true): EntityId {
        val id = world.createEntity()
        world.addComponent(id, shield)
        world.addComponent(id, HealthComponent(10, alive = alive))
        return id
    }

    // region absorbing

    /** The rule the whole mechanic reads by: the hit that breaks a bubble is spent breaking it. */
    @Test
    fun `a bubble takes a hit whole, even one bigger than it has left`() {
        val shield = ShieldComponent(points = 10)

        assertTrue(shield.absorb(40))

        assertEquals(0, shield.points)
        assertFalse(shield.isUp)
    }

    @Test
    fun `a bubble that is down lets the hit through`() {
        val shield = ShieldComponent(points = 10)
        shield.absorb(40)

        assertFalse(shield.absorb(5), "a broken bubble absorbed a second hit")
    }

    @Test
    fun `a hit that leaves the bubble standing wears it down`() {
        val shield = ShieldComponent(points = 30)

        shield.absorb(10)

        assertEquals(20, shield.points)
        assertTrue(shield.isUp)
        assertEquals(20f / 30f, shield.fraction)
    }

    @Test
    fun `breaking a bubble starts the ring it goes out in`() {
        val shield = ShieldComponent(points = 5)

        shield.absorb(5)

        assertEquals(ShieldSystem.POP_SECONDS, shield.popTime)
    }

    @Test
    fun `raising a bubble puts it back at full strength`() {
        val shield = ShieldComponent(points = 0)

        shield.raise(50)

        assertEquals(50, shield.points)
        assertEquals(50, shield.maxPoints)
        assertTrue(shield.isUp)
    }

    // endregion

    // region recharging

    @Test
    fun `a bubble with no recharge rate stays broken`() {
        val shield = ShieldComponent(points = 10)
        shielded(shield)
        shield.absorb(10)

        repeat(200) { world.update(0.05f, null) }

        assertEquals(0, shield.points)
    }

    @Test
    fun `a recharging bubble waits out its delay, then builds back to full and no further`() {
        val shield = ShieldComponent(points = 10, regenPerSecond = 5f, regenDelay = 1f)
        shielded(shield)
        shield.absorb(10)

        world.update(0.5f, null)
        assertEquals(0, shield.points, "recharged inside its delay")

        repeat(40) { world.update(0.1f, null) }
        assertEquals(10, shield.points)
    }

    /** At a few points a second, a tick is a fraction of a point; rounding it would recharge nothing. */
    @Test
    fun `a slow recharge is carried between ticks rather than rounded away`() {
        val shield = ShieldComponent(points = 10, regenPerSecond = 2f)
        shielded(shield)
        shield.absorb(4)

        // 2.4 points' worth, in hundredths of a second.
        repeat(120) { world.update(0.01f, null) }

        assertEquals(8, shield.points)
    }

    @Test
    fun `nothing dead recharges its bubble`() {
        val shield = ShieldComponent(points = 10, regenPerSecond = 50f)
        shielded(shield, alive = false)
        shield.absorb(10)

        repeat(20) { world.update(0.1f, null) }

        assertEquals(0, shield.points)
    }

    @Test
    fun `a hit's flare burns down`() {
        val shield = ShieldComponent(points = 10)
        shielded(shield)
        shield.absorb(1)

        world.update(1f, null)

        assertEquals(0f, shield.flash)
    }

    // endregion
}
