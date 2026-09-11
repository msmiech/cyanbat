package at.smiech.engine.ecs

import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WORLD_WIDTH = 480
private const val WORLD_HEIGHT = 320

class BounceSystemTest {

    private val world = World().apply {
        // The pair the game uses, and in the order it uses them: movement carries an entity into
        // an edge, and the bounce answers it on the same tick.
        addSystem(MovementSystem())
        addSystem(BounceSystem(WORLD_WIDTH, WORLD_HEIGHT))
    }

    private fun spawn(x: Float, y: Float, velocity: Vector2, bounces: Int): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, 10f, 10f)))
        world.addComponent(id, VelocityComponent(velocity))
        world.addComponent(id, BounceComponent(bounces))
        return id
    }

    private fun tick(times: Int = 1) = repeat(times) { world.update(0.019f, null) }

    private fun velocityOf(id: EntityId) = world.getComponent(id, VelocityComponent::class)!!.velocity
    private fun rectOf(id: EntityId) = world.getComponent(id, TransformComponent::class)!!.rect
    private fun bouncesLeft(id: EntityId) = world.getComponent(id, BounceComponent::class)!!.remaining

    // region the reflections

    @Test
    fun `an entity reaching the top comes back down`() {
        val id = spawn(x = 100f, y = 2f, velocity = Vector2(0f, -4f), bounces = 1)

        tick()

        assertEquals(4f, velocityOf(id).y)
        assertEquals(0, bouncesLeft(id))
    }

    @Test
    fun `an entity reaching the bottom comes back up`() {
        val id = spawn(x = 100f, y = WORLD_HEIGHT - 12f, velocity = Vector2(0f, 4f), bounces = 1)

        tick()

        assertEquals(-4f, velocityOf(id).y)
    }

    @Test
    fun `an entity reaching the right edge comes back left`() {
        val id = spawn(x = WORLD_WIDTH - 12f, y = 100f, velocity = Vector2(4f, 0f), bounces = 1)

        tick()

        assertEquals(-4f, velocityOf(id).x)
    }

    /** Only the leading edge counts, so the reflection turns the entity around rather than over. */
    @Test
    fun `a reflection leaves the other axis alone`() {
        val id = spawn(x = 100f, y = 2f, velocity = Vector2(3f, -4f), bounces = 1)

        tick()

        assertEquals(3f, velocityOf(id).x)
        assertEquals(4f, velocityOf(id).y)
    }

    // endregion

    // region what must not happen

    /**
     * The bug this system is most likely to have: an entity that ends a frame still overlapping an
     * edge, reflected again on the next frame, and again, until every bounce it owns is gone and it
     * is stuck to the wall. The nudge back inside is what prevents it.
     */
    @Test
    fun `one contact with an edge spends exactly one bounce`() {
        val id = spawn(x = 100f, y = 1f, velocity = Vector2(0f, -4f), bounces = 3)

        tick(times = 10)

        assertEquals(2, bouncesLeft(id))
        assertTrue(rectOf(id).top > 0f, "the entity is still stuck against the edge")
    }

    /** An entity already travelling away from a wall is not touching it as far as this is concerned. */
    @Test
    fun `an entity moving away from an edge is left alone`() {
        val id = spawn(x = 100f, y = -2f, velocity = Vector2(0f, 4f), bounces = 2)

        tick()

        assertEquals(4f, velocityOf(id).y)
        assertEquals(2, bouncesLeft(id))
    }

    /** A corner is two walls; taking both at once would send the entity straight back for two. */
    @Test
    fun `a corner costs one bounce, not two`() {
        val id = spawn(x = WORLD_WIDTH - 11f, y = 1f, velocity = Vector2(4f, -4f), bounces = 4)

        tick()

        assertEquals(3, bouncesLeft(id))
    }

    @Test
    fun `an entity out of bounces stops being reflected`() {
        val id = spawn(x = 100f, y = 2f, velocity = Vector2(0f, -4f), bounces = 0)

        tick()

        assertEquals(-4f, velocityOf(id).y, "a spent entity should carry on out of the frame")
    }

    /** Nothing without the component bounces: the bat and the scenery are not made of rubber. */
    @Test
    fun `an entity with no bounce component is untouched`() {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(100f, 2f, 10f, 10f)))
        world.addComponent(id, VelocityComponent(Vector2(0f, -4f)))

        tick()

        assertEquals(-4f, velocityOf(id).y)
    }

    // endregion

    /**
     * Several reflections in a row, which is what a multi-bounce shot actually does: up into the
     * ceiling, down the full height into the floor, and then out through the ceiling again with
     * nothing left to spend.
     */
    @Test
    fun `a shot crossing the frame spends its bounces one edge at a time`() {
        val id = spawn(x = 100f, y = 160f, velocity = Vector2(0f, -4f), bounces = 2)
        val reversals = mutableListOf<Float>()
        var previous = velocityOf(id).y

        repeat(300) {
            tick()
            val current = velocityOf(id).y
            if (current != previous) reversals += current
            previous = current
        }

        // Down off the ceiling, up off the floor, and then no more however far it travels.
        assertEquals(listOf(4f, -4f), reversals)
        assertEquals(0, bouncesLeft(id))
        assertTrue(rectOf(id).bottom < 0f, "a spent shot should carry on out of the frame")
    }
}
