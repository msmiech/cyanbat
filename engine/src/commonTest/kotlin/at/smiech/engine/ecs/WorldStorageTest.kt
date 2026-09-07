package at.smiech.engine.ecs

import at.smiech.engine.Input
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the dense-array storage underneath [World]: id recycling, growth past the initial
 * capacity, and the mapper-based iteration the systems run on. The behavioural contract
 * ([WorldTest]) is what these have to keep intact - they only pin down the parts of it that the
 * storage layer could plausibly break.
 */
class WorldStorageTest {

    private fun World.spawn(x: Float = 0f): EntityId {
        val id = createEntity()
        addComponent(id, TransformComponent(Rect.fromLTWH(x, 0f, 10f, 10f)))
        return id
    }

    /**
     * Ids are handed back out once a removal lands. Whatever the recycled slot held before must be
     * gone: leaking a component into the next entity would silently give it behaviour it never
     * asked for.
     */
    @Test
    fun `a recycled id starts with no components`() {
        val world = World()
        val first = world.spawn()
        world.addComponent(first, VelocityComponent(Vector2(1f, 0f)))
        world.removeEntity(first)
        world.update(0.016f, null)

        val second = world.createEntity()
        assertEquals(first, second, "the freed id should be reused")
        assertFalse(world.hasComponent(second, TransformComponent::class))
        assertFalse(world.hasComponent(second, VelocityComponent::class))
        assertNull(world.getComponent(second, TransformComponent::class))
    }

    /**
     * Recycling only happens when removals are finalised, at the end of an update. Anything else
     * would let a spawn inside a system - an explosion raised by a collision, say - land on top of
     * an entity that is still being processed.
     */
    @Test
    fun `ids are not recycled in the middle of an update`() {
        val world = World()
        val doomed = world.spawn()
        world.removeEntity(doomed)

        val spawnedSameFrame = world.createEntity()
        assertTrue(spawnedSameFrame != doomed, "the id was reused before the frame ended")
        assertTrue(world.hasComponent(doomed, TransformComponent::class))
    }

    @Test
    fun `storage grows well past its initial capacity`() {
        val world = World()
        val ids = (0 until 500).map { world.spawn(x = it.toFloat()) }

        assertEquals(500, ids.toSet().size)
        assertContentEquals(ids, world.query(TransformComponent::class))
        assertEquals(499f, world.getComponent(ids.last(), TransformComponent::class)!!.rect.left)
    }

    @Test
    fun `a component type first seen after the world grew reaches every entity`() {
        val world = World()
        val ids = (0 until 200).map { world.spawn() }
        // HealthComponent is registered here, long after the entity arrays were resized.
        ids.forEach { world.addComponent(it, HealthComponent(lives = 2)) }

        assertEquals(2, world.getComponent(ids.last(), HealthComponent::class)!!.lives)
        assertEquals(200, world.query(HealthComponent::class).size)
    }

    @Test
    fun `mapper reads and query agree`() {
        val world = World()
        val transforms = world.mapper(TransformComponent::class)
        val velocities = world.mapper(VelocityComponent::class)

        val bare = world.spawn()
        val moving = world.spawn()
        world.addComponent(moving, VelocityComponent(Vector2(1f, 2f)))

        assertNull(transforms[-1])
        assertFalse(velocities.has(bare))
        assertTrue(velocities.has(moving))
        assertEquals(1f, velocities.require(moving).velocity.x)

        val visited = mutableListOf<EntityId>()
        world.forEach(transforms) { visited += it }
        assertContentEquals(listOf(bare, moving), visited)

        visited.clear()
        world.forEach(transforms, velocities) { visited += it }
        assertContentEquals(listOf(moving), visited)

        assertEquals(2, world.count(transforms))
        assertEquals(1, world.count(transforms, velocities))
    }

    /**
     * Systems spawn while they iterate - the background scroller does exactly this. The new entity
     * has to wait for the next pass, or a system that spawns unconditionally would never finish.
     */
    @Test
    fun `entities spawned during iteration are not visited by that pass`() {
        val world = World()
        val transforms = world.mapper(TransformComponent::class)
        world.spawn()

        var visits = 0
        world.forEach(transforms) {
            visits++
            if (visits < 10) world.spawn()
        }

        assertEquals(1, visits)
        assertEquals(2, world.count(transforms))
    }

    /** Removal is deferred, so a system that reaps mid-pass still sees the rest of the batch. */
    @Test
    fun `entities removed during iteration are still visited by that pass`() {
        val world = World()
        val transforms = world.mapper(TransformComponent::class)
        val ids = List(3) { world.spawn() }

        val visited = mutableListOf<EntityId>()
        world.forEach(transforms) {
            world.removeEntity(ids[2])
            visited += it
        }

        assertContentEquals(ids, visited)
    }

    /** A world can outlive far more spawns than it ever holds at once. */
    @Test
    fun `churning entities does not grow the live set`() {
        val world = World()
        val transforms = world.mapper(TransformComponent::class)
        val survivor = world.spawn()

        repeat(1000) {
            val id = world.spawn()
            world.removeEntity(id)
            world.update(0.016f, null)
        }

        assertEquals(1, world.count(transforms))
        assertContentEquals(listOf(survivor), world.query(TransformComponent::class))
    }

    /** Ids the world never issued are simply not there to reap. */
    @Test
    fun `removing an id the world never issued is harmless`() {
        val world = World()
        val id = world.spawn()

        world.removeEntity(-1)
        world.removeEntity(9999)
        world.update(0.016f, null)

        assertTrue(world.hasComponent(id, TransformComponent::class))
    }

    @Test
    fun `systems are attached when they are added`() {
        var attachedTo: World? = null
        val world = World()
        world.addSystem(object : GameSystem() {
            override fun onAttach(world: World) {
                attachedTo = world
            }

            override fun update(world: World, deltaTime: Float, input: Input?) = Unit
        })
        assertSame(world, attachedTo)
    }
}
