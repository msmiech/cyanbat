package at.smiech.engine.ecs

import at.smiech.engine.Input
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingSystem : GameSystem() {
    val seen = mutableListOf<Int>()
    var updates = 0
    override fun update(world: World, deltaTime: Float, input: Input?) {
        updates++
        seen += world.query(TransformComponent::class).size
    }
}

class WorldTest {

    private fun World.spawn(x: Float = 0f): EntityId {
        val id = createEntity()
        addComponent(id, TransformComponent(Rect.fromLTWH(x, 0f, 10f, 10f)))
        return id
    }

    @Test
    fun `entities get distinct ids`() {
        val world = World()
        assertEquals(3, setOf(world.spawn(), world.spawn(), world.spawn()).size)
    }

    @Test
    fun `components round trip and missing ones read null`() {
        val world = World()
        val id = world.spawn()
        assertEquals(Rect.fromLTWH(0f, 0f, 10f, 10f), world.getComponent(id, TransformComponent::class)!!.rect)
        assertNull(world.getComponent(id, VelocityComponent::class))
        assertTrue(world.hasComponent(id, TransformComponent::class))
        assertFalse(world.hasComponent(id, VelocityComponent::class))
    }

    @Test
    fun `query returns only entities carrying every requested component`() {
        val world = World()
        val bare = world.spawn()
        val moving = world.spawn()
        world.addComponent(moving, VelocityComponent(Vector2.Zero))

        assertContentEquals(listOf(bare, moving), world.query(TransformComponent::class))
        assertContentEquals(listOf(moving), world.query(TransformComponent::class, VelocityComponent::class))
        assertContentEquals(emptyList(), world.query(HealthComponent::class))
    }

    /**
     * Removal is deferred to the end of the frame. Systems iterating a query must therefore still
     * see a removed entity for the rest of the current update - collision handlers rely on the
     * component still being readable after something is marked dead.
     */
    @Test
    fun `removal is deferred until the end of the update`() {
        val world = World()
        val id = world.spawn()
        world.removeEntity(id)

        assertTrue(world.hasComponent(id, TransformComponent::class), "entity vanished before the frame ended")

        world.update(0.016f, null)

        assertFalse(world.hasComponent(id, TransformComponent::class))
        assertNull(world.getComponent(id, TransformComponent::class))
        assertContentEquals(emptyList(), world.query(TransformComponent::class))
    }

    @Test
    fun `removing the same entity twice is harmless`() {
        val world = World()
        val id = world.spawn()
        world.removeEntity(id)
        world.removeEntity(id)
        world.update(0.016f, null)
        world.update(0.016f, null)
        assertContentEquals(emptyList(), world.query(TransformComponent::class))
    }

    @Test
    fun `systems run every update and observe the current entity set`() {
        val world = World()
        val system = RecordingSystem()
        world.addSystem(system)

        world.spawn()
        world.update(0.016f, null)
        val second = world.spawn()
        world.update(0.016f, null)
        world.removeEntity(second)
        world.update(0.016f, null)
        world.update(0.016f, null)

        assertEquals(4, system.updates)
        // The third update still sees both: the removal only lands once it finishes.
        assertContentEquals(listOf(1, 2, 2, 1), system.seen)
    }

    @Test
    fun `movement system advances transforms by their velocity`() {
        val world = World()
        world.addSystem(MovementSystem())
        val id = world.spawn()
        world.addComponent(id, VelocityComponent(Vector2(2f, -1f)))

        world.update(0.016f, null)
        world.update(0.016f, null)

        val rect = world.getComponent(id, TransformComponent::class)!!.rect
        assertEquals(4f, rect.left)
        assertEquals(-2f, rect.top)
        assertEquals(10f, rect.width, "movement must not resize the entity")
    }

    @Test
    fun `lifetime system reaps entities that leave the left edge`() {
        val world = World()
        world.addSystem(LifetimeSystem(worldWidth = 480))

        val offscreen = world.spawn(x = -50f)
        world.addComponent(offscreen, LifetimeComponent(removeIfOutOfBounds = true))
        val onscreen = world.spawn(x = 100f)
        world.addComponent(onscreen, LifetimeComponent(removeIfOutOfBounds = true))
        val pinned = world.spawn(x = -50f)
        world.addComponent(pinned, LifetimeComponent(removeIfOutOfBounds = false))

        world.update(0.016f, null)

        assertFalse(world.hasComponent(offscreen, TransformComponent::class))
        assertTrue(world.hasComponent(onscreen, TransformComponent::class))
        assertTrue(world.hasComponent(pinned, TransformComponent::class), "opted out of bounds culling")
    }
}
