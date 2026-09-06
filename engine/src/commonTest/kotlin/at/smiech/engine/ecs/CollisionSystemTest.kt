package at.smiech.engine.ecs

import at.smiech.engine.math.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollisionSystemTest {

    private class Harness {
        val hits = mutableListOf<Pair<EntityId, EntityId>>()
        val world = World().apply {
            addSystem(CollisionSystem { a, b -> hits += a to b })
        }

        fun spawn(group: CollisionGroup, x: Float, tolerance: Float = 0f): EntityId {
            val id = world.createEntity()
            world.addComponent(id, TransformComponent(Rect.fromLTWH(x, 0f, 20f, 20f)))
            world.addComponent(id, CollisionComponent(tolerance, group))
            return id
        }

        fun step() = world.update(0.016f, null)
    }

    @Test
    fun `overlapping entities from hostile groups collide`() {
        val h = Harness()
        val player = h.spawn(CollisionGroup.PLAYER, 0f)
        val enemy = h.spawn(CollisionGroup.ENEMY, 10f)
        h.step()
        assertEquals(listOf(player to enemy), h.hits)
    }

    @Test
    fun `separated entities do not collide`() {
        val h = Harness()
        h.spawn(CollisionGroup.PLAYER, 0f)
        h.spawn(CollisionGroup.ENEMY, 100f)
        h.step()
        assertTrue(h.hits.isEmpty())
    }

    @Test
    fun `entities in the same group never collide`() {
        val h = Harness()
        h.spawn(CollisionGroup.ENEMY, 0f)
        h.spawn(CollisionGroup.ENEMY, 5f)
        h.step()
        assertTrue(h.hits.isEmpty(), "enemies should pass through each other")
    }

    @Test
    fun `shooters are immune to their own projectiles in both orderings`() {
        for ((first, second) in listOf(
            CollisionGroup.PLAYER to CollisionGroup.PLAYER_PROJECTILE,
            CollisionGroup.PLAYER_PROJECTILE to CollisionGroup.PLAYER,
            CollisionGroup.ENEMY to CollisionGroup.ENEMY_PROJECTILE,
            CollisionGroup.ENEMY_PROJECTILE to CollisionGroup.ENEMY,
        )) {
            val h = Harness()
            h.spawn(first, 0f)
            h.spawn(second, 5f)
            h.step()
            assertTrue(h.hits.isEmpty(), "$first should not be hit by $second")
        }
    }

    @Test
    fun `a projectile still hits the opposing side`() {
        val h = Harness()
        h.spawn(CollisionGroup.PLAYER_PROJECTILE, 0f)
        h.spawn(CollisionGroup.ENEMY, 5f)
        h.step()
        assertEquals(1, h.hits.size)
    }

    /** Tolerance shrinks both hitboxes, so a graze that would touch at zero tolerance misses. */
    @Test
    fun `tolerance forgives a graze`() {
        val grazing = Harness()
        grazing.spawn(CollisionGroup.PLAYER, 0f, tolerance = 0f)
        grazing.spawn(CollisionGroup.ENEMY, 19f, tolerance = 0f)
        grazing.step()
        assertEquals(1, grazing.hits.size, "a 1px overlap is a hit without tolerance")

        val forgiving = Harness()
        forgiving.spawn(CollisionGroup.PLAYER, 0f, tolerance = 5f)
        forgiving.spawn(CollisionGroup.ENEMY, 19f, tolerance = 5f)
        forgiving.step()
        assertTrue(forgiving.hits.isEmpty(), "the same overlap should be forgiven with tolerance")
    }

    @Test
    fun `each colliding pair is reported once per frame`() {
        val h = Harness()
        h.spawn(CollisionGroup.PLAYER, 0f)
        h.spawn(CollisionGroup.ENEMY, 5f)
        h.spawn(CollisionGroup.OBSTACLE, 10f)
        h.step()
        val unordered = h.hits.map { setOf(it.first, it.second) }
        assertEquals(unordered.size, unordered.distinct().size, "a pair was reported twice: ${h.hits}")
    }
}
