package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.util.FROST_DRIFT
import at.smiech.cyanbat.util.FROST_THAW_SECONDS
import at.smiech.cyanbat.util.FROST_TINT
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.AnimationComponent
import at.smiech.engine.ecs.AnimationSystem
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.MovementSystem
import at.smiech.engine.ecs.PaceComponent
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TintComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.VelocityComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.ecs.WeaponSystem
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object CreatureSheet : Pixmap {
    override val width = 128
    override val height = 29
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

class FrostSystemTest {

    private val thawed = mutableListOf<EntityId>()
    private val fired = mutableListOf<EntityId>()

    private val world = World().apply {
        addSystem(MovementSystem())
        addSystem(FrostSystem { thawed += it })
        addSystem(WeaponSystem { fired += it })
        addSystem(AnimationSystem())
    }

    /** An ordinary enemy, flying left and firing every half second. */
    private fun enemy(x: Float = 300f): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, 100f, 32f, 29f)))
        world.addComponent(id, VelocityComponent(Vector2(-2f, 0.5f)))
        world.addComponent(id, PaceComponent())
        world.addComponent(id, WeaponComponent(0.5f))
        world.addComponent(id, SpriteComponent(CreatureSheet, srcWidth = 32, srcHeight = 29))
        world.addComponent(id, AnimationComponent(32, 29, 4, 0.1f))
        return id
    }

    private fun rectOf(id: EntityId) = world.getComponent(id, TransformComponent::class)!!.rect

    private fun step(seconds: Float) {
        var left = seconds
        while (left > 0f) {
            world.update(TICK, null)
            left -= TICK
        }
    }

    /**
     * Its flight, its wingbeat and its gun all stop; all that moves it is the scenery's drift, so it
     * hangs in the world rather than on the screen.
     */
    @Test
    fun `something frozen drifts with the scenery and does nothing else`() {
        val enemy = enemy()
        val before = rectOf(enemy)
        val frame = world.getComponent(enemy, AnimationComponent::class)!!.currentFrame

        FrostSystem.freeze(world, enemy, 1f)
        world.update(TICK, null)

        assertEquals(before.offset(FROST_DRIFT, 0f), rectOf(enemy))
        step(0.8f)
        assertTrue(fired.isEmpty(), "it fired while frozen")
        assertEquals(
            frame,
            world.getComponent(enemy, AnimationComponent::class)!!.currentFrame,
            "its wings beat"
        )
        assertEquals(before.top, rectOf(enemy).top, "it went on flying up and down")
    }

    @Test
    fun `it comes free when its time is up and is handed back`() {
        val enemy = enemy()
        FrostSystem.freeze(world, enemy, 0.5f)

        step(0.45f)
        assertTrue(thawed.isEmpty(), "thawed early")
        assertTrue(FrostSystem.isFrozen(world, enemy))

        step(0.1f)
        assertEquals(listOf(enemy), thawed)
        assertFalse(FrostSystem.isFrozen(world, enemy))
        assertEquals(
            0,
            world.getComponent(enemy, TintComponent::class)!!.color ushr 24,
            "still blue"
        )
    }

    @Test
    fun `a second beam never cuts a freeze short`() {
        val enemy = enemy()
        FrostSystem.freeze(world, enemy, 2f)

        FrostSystem.freeze(world, enemy, 0.5f)
        step(1f)

        assertTrue(FrostSystem.isFrozen(world, enemy), "the shorter freeze won")
    }

    /** Blue the moment it is caught, and thinning out over the last of the freeze, as a warning. */
    @Test
    fun `it is washed blue and the wash thins as it thaws`() {
        val enemy = enemy()
        FrostSystem.freeze(world, enemy, 1f)
        val tint = world.getComponent(enemy, TintComponent::class)!!
        assertEquals(FROST_TINT, tint.color, "not blue at once")

        step(1f - FROST_THAW_SECONDS / 2f)

        val alpha = tint.color ushr 24
        assertTrue(
            alpha in 1 until (FROST_TINT ushr 24),
            "halfway through the thaw the wash is at $alpha"
        )
        assertEquals(FROST_TINT and 0xFFFFFF, tint.color and 0xFFFFFF, "the blue changed hue")
    }

    @Test
    fun `something frozen that drifts off the left is gone`() {
        val enemy = enemy(x = -31f)
        world.getComponent(enemy, VelocityComponent::class)!!.velocity = Vector2(3f, 0f)
        FrostSystem.freeze(world, enemy, 5f)

        step(0.1f)

        assertTrue(
            world.query(FrostComponent::class).isEmpty(),
            "a frozen enemy hung on past the left edge"
        )
    }

    private companion object {
        const val TICK = 0.019f
    }
}
