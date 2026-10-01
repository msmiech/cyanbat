package at.smiech.engine.ecs

import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TICK = 0.019f

/** An enemy sheet: three 32x29 strips of four frames. */
private class StripSheet : Pixmap {
    override val width = 32 * 4 * 3
    override val height = 29
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/**
 * An entity slowed by a [PaceComponent] runs on a clock of its own. What it promises is that a slowed
 * enemy is the same enemy, slower: its patterns keep their shape and only take longer.
 */
class PaceComponentTest {

    private val world = World().apply {
        addSystem(EnemyBehaviorSystem())
        addSystem(MovementSystem())
        addSystem(AnimationSystem())
    }

    private fun enemy(
        type: EnemyMovementType,
        x: Float,
        y: Float,
        holdX: Float = 0f,
        baseSpeedX: Float = -1.6f,
        pace: Float = 1f,
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, 28f, 29f)))
        world.addComponent(id, VelocityComponent(Vector2(baseSpeedX, 0f)))
        world.addComponent(id, EnemyBehaviorComponent(type, y, holdX = holdX, baseSpeedX = baseSpeedX))
        world.addComponent(id, PaceComponent(motion = pace))
        return id
    }

    private fun player(x: Float, y: Float) {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, 45f, 40f)))
        world.addComponent(id, PlayerControlComponent())
        world.addComponent(id, HealthComponent(100))
    }

    private fun rectOf(id: EntityId) = world.getComponent(id, TransformComponent::class)!!.rect

    @Test
    fun `a slowed entity covers its share of the distance`() {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(100f, 100f, 10f, 10f)))
        world.addComponent(id, VelocityComponent(Vector2(-2f, 1f)))
        world.addComponent(id, PaceComponent(motion = 0.5f))

        world.update(TICK, null)

        assertEquals(99f, rectOf(id).left, 0.001f)
        assertEquals(100.5f, rectOf(id).top, 0.001f)
    }

    /**
     * Slowing only the velocity would have flown a smaller loop in the same time. A slowed clock
     * flies the same loop, and takes longer over it.
     */
    @Test
    fun `a slowed loop is the same size and takes longer`() {
        val full = enemy(EnemyMovementType.LOOP, x = 330f, y = 150f, holdX = 320f, baseSpeedX = -1.7f)
        val slowed = enemy(EnemyMovementType.LOOP, x = 330f, y = 150f, holdX = 320f, baseSpeedX = -1.7f, pace = 0.5f)
        var fullTop = Float.MAX_VALUE
        var slowedTop = Float.MAX_VALUE
        var fullLooped = 0
        var slowedLooped = 0

        repeat((6f / TICK).toInt()) {
            world.update(TICK, null)
            fullTop = minOf(fullTop, rectOf(full).top)
            slowedTop = minOf(slowedTop, rectOf(slowed).top)
            if (world.getComponent(full, EnemyBehaviorComponent::class)!!.state == 1) fullLooped++
            if (world.getComponent(slowed, EnemyBehaviorComponent::class)!!.state == 1) slowedLooped++
        }

        assertEquals(fullTop, slowedTop, 3f, "the slowed loop is a different size")
        assertEquals(2f, slowedLooped.toFloat() / fullLooped, 0.1f, "the slowed loop did not take twice as long")
    }

    /**
     * A leap is aimed at the player's height, and a slowed one has to get there too: thrown as hard
     * and fallen on its own time, it climbs as high, only slower.
     */
    @Test
    fun `a slowed leap still tops out at the player's height`() {
        player(x = 60f, y = 130f) // centered at 150
        val leaper = enemy(EnemyMovementType.LEAP, x = 480f, y = 310f, holdX = 300f, pace = 0.6f)
        var apex = Float.MAX_VALUE

        repeat((10f / TICK).toInt()) {
            world.update(TICK, null)
            apex = minOf(apex, rectOf(leaper).centerY)
        }

        assertEquals(150f, apex, 8f, "the slowed leap missed the player's height")
    }

    @Test
    fun `a slowed wingbeat beats slower`() {
        val id = world.createEntity()
        val sprite = SpriteComponent(StripSheet(), srcWidth = 32)
        world.addComponent(id, sprite)
        world.addComponent(id, AnimationComponent(32, 29, 4, 0.1f))
        world.addComponent(id, PaceComponent(motion = 0.5f))

        world.update(0.15f, null)
        assertEquals(0, sprite.srcX, "it beat at full pace")

        world.update(0.1f, null)
        assertEquals(32, sprite.srcX, "it never beat at all")
    }

    @Test
    fun `a slowed gun fires that much less often`() {
        val shots = mutableListOf<EntityId>()
        val guns = World().apply { addSystem(WeaponSystem { shots += it }) }
        fun gun(fire: Float) = guns.createEntity().also { id ->
            guns.addComponent(id, TransformComponent(Rect.fromLTWH(0f, 0f, 10f, 10f)))
            guns.addComponent(id, WeaponComponent(interval = 1f))
            guns.addComponent(id, PaceComponent(fire = fire))
        }
        val full = gun(1f)
        val halved = gun(0.5f)

        repeat((4.01f / TICK).toInt()) { guns.update(TICK, null) }

        assertEquals(4, shots.count { it == full })
        assertEquals(2, shots.count { it == halved })
    }

    /** Its motion and its fire are separate dials: slowing one leaves the other alone. */
    @Test
    fun `fire slowed alone leaves the flight at full pace`() {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(100f, 100f, 10f, 10f)))
        world.addComponent(id, VelocityComponent(Vector2(-2f, 0f)))
        world.addComponent(id, PaceComponent(fire = 0.25f))

        world.update(TICK, null)

        assertEquals(98f, rectOf(id).left, 0.001f, "fire slowed the flight")
    }
}
