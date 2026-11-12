package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.service.EntityFactory
import at.smiech.cyanbat.util.CACO_IMP_EMBER_FUSE_SECONDS
import at.smiech.cyanbat.util.EMBER_FRAME
import at.smiech.cyanbat.util.EMBER_FRAME_COUNT
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.MovementSystem
import at.smiech.engine.ecs.TintComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.VelocityComponent
import at.smiech.engine.ecs.World
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class EmberSheet : Pixmap {
    override val width = EMBER_FRAME * EMBER_FRAME_COUNT
    override val height = EMBER_FRAME
    override fun dispose() = Unit
}

/** The Caco Imp's embers: thrown, slowed to a hang, flashing, and burst once their fuse is out. */
class EmberSystemTest {
    private val bursts = mutableListOf<Pair<Float, Float>>()
    private val world = World().apply {
        addSystem(MovementSystem())
        addSystem(
            EmberSystem { id ->
                val rect = getComponent(id, TransformComponent::class)!!.rect
                bursts += rect.centerX to rect.centerY
            }
        )
    }
    private val factory = EntityFactory(world, lit = false)

    private fun ember(): EntityId = factory.createEmber(
        centerX = 400f, centerY = 180f, pixmap = EmberSheet(), damage = 30, variant = 3,
        angleDegrees = 0f, speed = 3.2f,
    )

    private fun run(seconds: Float) = repeat((seconds / TICK_INITIAL).roundToInt()) {
        world.update(TICK_INITIAL, null)
    }

    private fun speedOf(id: EntityId): Float {
        val v = world.getComponent(id, VelocityComponent::class)!!.velocity
        return hypot(v.x, v.y)
    }

    @Test
    fun `it is an enemy shot thrown to the left`() {
        val id = ember()
        assertEquals(
            CollisionGroup.ENEMY_PROJECTILE,
            world.getComponent(id, CollisionComponent::class)!!.group
        )
        run(0.1f)
        assertTrue(world.getComponent(id, TransformComponent::class)!!.rect.centerX < 400f)
    }

    @Test
    fun `it slows to a hang and flashes before it bursts`() {
        val id = ember()
        val thrown = speedOf(id)
        run(CACO_IMP_EMBER_FUSE_SECONDS * 0.3f)
        assertEquals(0, world.getComponent(id, TintComponent::class)!!.color, "it flashed early")
        run(CACO_IMP_EMBER_FUSE_SECONDS * 0.5f)
        // All but hanging: drifting at under a fifth of its throw, a pixel or so a tick.
        assertTrue(speedOf(id) < thrown * 0.2f, "still flying at ${speedOf(id)}")

        var flashed = false
        repeat((CACO_IMP_EMBER_FUSE_SECONDS * 0.15f / TICK_INITIAL).toInt()) {
            world.update(TICK_INITIAL, null)
            if ((world.getComponent(id, TintComponent::class)!!.color ushr 24) > 100) flashed = true
        }
        assertTrue(flashed, "no warning before it burst")
    }

    @Test
    fun `it bursts once where it hangs and is gone`() {
        val id = ember()
        run(CACO_IMP_EMBER_FUSE_SECONDS - 0.1f)
        assertEquals(emptyList(), bursts)
        val at = world.getComponent(id, TransformComponent::class)!!.rect
        run(0.3f)

        assertEquals(1, bursts.size)
        // Where it hung, give or take the last of its drift.
        assertEquals(at.centerX, bursts[0].first, 5f)
        assertEquals(at.centerY, bursts[0].second, 5f)
        assertTrue(world.query(EmberComponent::class).isEmpty())
    }

    /** Shot down or spent on the bat, it is gone like any shot, and its ring with it. */
    @Test
    fun `one put out before its fuse is up never bursts`() {
        val id = ember()
        run(0.5f)
        world.removeEntity(id)
        run(CACO_IMP_EMBER_FUSE_SECONDS)
        assertEquals(emptyList(), bursts)
    }
}
