package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.util.ORB_RADIUS
import at.smiech.cyanbat.util.ORB_SECONDS_PER_TURN
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrbitSystemTest {

    private var center: Rect? = Rect.fromLTWH(200f, 150f, 45f, 40f)
    private val orbit = OrbitSystem { center }
    private val world = World().apply { addSystem(orbit) }

    private fun orb(offset: Float): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(0f, 0f, 14f, 14f)))
        world.addComponent(id, OrbComponent(offset))
        return id
    }

    private fun step(seconds: Float = TICK) {
        var left = seconds
        while (left > 0f) {
            world.update(TICK, null)
            left -= TICK
        }
    }

    /** Where [id] is on the ring, clockwise from the right as the frame is drawn, in 0 until 2 pi. */
    private fun angleOf(id: EntityId): Float {
        val rect = world.getComponent(id, TransformComponent::class)!!.rect
        val around = center!!
        val angle = atan2(rect.centerY - around.centerY, rect.centerX - around.centerX)
        return ((angle + 2 * PI) % (2 * PI)).toFloat()
    }

    private fun distanceOf(id: EntityId): Float {
        val rect = world.getComponent(id, TransformComponent::class)!!.rect
        return hypot(rect.centerX - center!!.centerX, rect.centerY - center!!.centerY)
    }

    @Test
    fun `an orb goes round the bat at its radius`() {
        val orb = orb(0f)

        repeat(40) {
            step()
            assertEquals(ORB_RADIUS, distanceOf(orb), 0.01f)
        }
    }

    /** The frame's y runs down, so from the right of the ring, clockwise is down. */
    @Test
    fun `the ring turns clockwise as the frame is drawn`() {
        val orb = orb(0f)
        step()
        val rect = world.getComponent(orb, TransformComponent::class)!!.rect

        assertTrue(rect.centerX > center!!.centerX, "should start on the right")
        assertTrue(rect.centerY > center!!.centerY, "and go down from there, not up")
    }

    @Test
    fun `the ring goes round once a turn`() {
        val orb = orb(0f)
        step()
        val start = angleOf(orb)

        step(ORB_SECONDS_PER_TURN)

        val drift = abs(angleOf(orb) - start)
        assertTrue(drift < 0.1f || drift > 2 * PI - 0.1f, "a turn later it is ${drift} radians from where it was")
    }

    @Test
    fun `the ring follows the bat`() {
        val orb = orb(0f)
        step()
        center = center!!.offset(100f, -40f)

        step()

        assertEquals(ORB_RADIUS, distanceOf(orb), 0.01f)
    }

    /** Placed at their shares, three orbs are a third of a turn apart and stay that way. */
    @Test
    fun `orbs share the circle evenly`() {
        val orbs = List(3) { orb(OrbitSystem.shareOf(it, 3)) }

        step(1f)

        val angles = orbs.map(::angleOf).sorted()
        val gaps = angles.zipWithNext { a, b -> b - a } + (angles.first() + 2 * PI.toFloat() - angles.last())
        for (gap in gaps) assertEquals((2 * PI / 3).toFloat(), gap, 0.01f)
    }

    /**
     * A new orb comes in at its share of the ring as it will be, and the ones already there ease over
     * to theirs: within a second, two orbs are half a turn apart.
     */
    @Test
    fun `the ring spaces itself out round a new orb`() {
        val first = orb(OrbitSystem.shareOf(0, 1))
        step(0.5f)
        val second = orb(OrbitSystem.shareOf(1, 2))

        step(1.5f)

        val gap = abs(angleOf(first) - angleOf(second))
        assertEquals(PI.toFloat(), gap, 0.05f)
    }

    @Test
    fun `with no bat to circle the orbs stay where they are`() {
        val orb = orb(0f)
        step()
        val before = world.getComponent(orb, TransformComponent::class)!!.rect
        center = null

        step(0.5f)

        assertEquals(before, world.getComponent(orb, TransformComponent::class)!!.rect)
    }

    /** For an orb just made, which would otherwise sit on the bat until the next tick. */
    @Test
    fun `arranging places an orb on the ring without turning it`() {
        val orb = orb(PI.toFloat() / 2)

        orbit.arrange(world)

        assertEquals(0f, orbit.turn)
        assertEquals(ORB_RADIUS, distanceOf(orb), 0.01f)
        assertEquals(PI.toFloat() / 2, angleOf(orb), 0.01f)
    }

    private companion object {
        const val TICK = 0.019f
    }
}
