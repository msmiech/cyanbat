package at.smiech.engine.ecs

import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TICK = 0.019f

/**
 * The forest's movement patterns. Each test drives a pattern the way the game does - behavior,
 * then movement, a fixed tick at a time - and checks the one thing that pattern promises.
 */
class EnemyBehaviorSystemTest {

    private val world = World().apply {
        addSystem(EnemyBehaviorSystem())
        addSystem(MovementSystem())
    }

    private fun enemy(
        type: EnemyMovementType,
        x: Float,
        y: Float,
        laneY: Float = y,
        offsetY: Float = 0f,
        phase: Float = 0f,
        holdX: Float = 0f,
        baseSpeedX: Float = -1.5f,
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, 28f, 29f)))
        world.addComponent(id, VelocityComponent(Vector2(baseSpeedX, 0f)))
        world.addComponent(
            id,
            EnemyBehaviorComponent(
                type, laneY, holdX = holdX, baseSpeedX = baseSpeedX, offsetY = offsetY, phase = phase,
            )
        )
        return id
    }

    private fun player(x: Float, y: Float): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, 45f, 40f)))
        world.addComponent(id, PlayerControlComponent())
        world.addComponent(id, HealthComponent(100))
        return id
    }

    private fun rectOf(id: EntityId) = world.getComponent(id, TransformComponent::class)!!.rect
    private fun velocityOf(id: EntityId) = world.getComponent(id, VelocityComponent::class)!!.velocity
    private fun behaviorOf(id: EntityId) = world.getComponent(id, EnemyBehaviorComponent::class)!!

    private fun run(seconds: Float) = repeat((seconds / TICK).toInt()) { world.update(TICK, null) }

    // region formation

    /** Nobody steers by anybody else, so the shape has to survive on shared maths alone. */
    @Test
    fun `a formation keeps its shape while it flies`() {
        val leader = enemy(EnemyMovementType.FORMATION, x = 480f, y = 150f)
        val wing = enemy(EnemyMovementType.FORMATION, x = 502f, y = 167f, laneY = 150f, offsetY = 17f)

        repeat(6) {
            run(0.5f)
            assertEquals(22f, rectOf(wing).left - rectOf(leader).left, 0.01f, "the ranks drifted apart")
            assertEquals(17f, rectOf(wing).top - rectOf(leader).top, 0.5f, "the wing left its place")
        }
    }

    @Test
    fun `a formation sweeps up and down as it comes`() {
        val leader = enemy(EnemyMovementType.FORMATION, x = 480f, y = 150f)
        val tops = mutableListOf<Float>()

        repeat(40) {
            run(0.1f)
            tops += rectOf(leader).top
        }

        assertTrue(tops.max() - tops.min() > 40f, "it flew a straight line")
        assertTrue(rectOf(leader).left < 480f, "it never closed")
    }

    // endregion

    // region swarm

    @Test
    fun `swarm members sway together around their shared path`() {
        val a = enemy(EnemyMovementType.SWARM, x = 480f, y = 140f, laneY = 150f, offsetY = -10f, phase = 0f)
        val b = enemy(EnemyMovementType.SWARM, x = 490f, y = 170f, laneY = 150f, offsetY = 20f, phase = 2f)

        run(3f)

        // Each buzzes by a few pixels about its own place; the places stay 30 apart.
        val gap = rectOf(b).top - rectOf(a).top
        assertTrue(abs(gap - 30f) < 16f, "the swarm came apart: gap $gap")
    }

    // endregion

    // region hover

    @Test
    fun `a hoverer stops on station, then leaves`() {
        val hoverer = enemy(EnemyMovementType.HOVER, x = 480f, y = 150f, holdX = 360f)

        run(4f)
        val onStation = rectOf(hoverer).left
        assertTrue(onStation <= 360f && onStation > 340f, "it did not stop at its station: $onStation")
        run(1f)
        assertEquals(onStation, rectOf(hoverer).left, 0.01f, "it drifted off station")

        run(6f)
        assertTrue(rectOf(hoverer).left < onStation - 50f, "it never left")
    }

    @Test
    fun `a hoverer drifts into the player's lane`() {
        player(x = 60f, y = 40f)
        val hoverer = enemy(EnemyMovementType.HOVER, x = 480f, y = 200f, holdX = 400f)

        run(4.5f)

        assertTrue(rectOf(hoverer).top < 160f, "it stayed in its own lane at ${rectOf(hoverer).top}")
    }

    // endregion

    // region dive

    @Test
    fun `a diver tells before it commits`() {
        player(x = 60f, y = 200f)
        val diver = enemy(EnemyMovementType.DIVE, x = 360f, y = 60f, holdX = 380f)

        world.update(TICK, null)

        assertEquals(1, behaviorOf(diver).state)
        assertTrue(velocityOf(diver).x > 0f, "no backing off, so no tell")
    }

    @Test
    fun `a diver commits to where the player was, and flies straight`() {
        player(x = 60f, y = 220f)
        val diver = enemy(EnemyMovementType.DIVE, x = 360f, y = 40f, holdX = 380f)

        run(0.6f)
        val committed = velocityOf(diver)
        assertTrue(committed.x < -1f, "it is not diving toward the player")
        assertTrue(committed.y > 1f, "it is not diving down toward the player")

        run(0.3f)
        assertEquals(committed, velocityOf(diver), "a committed dive changed course")
    }

    @Test
    fun `a diver with nobody to dive at flies straight on`() {
        val diver = enemy(EnemyMovementType.DIVE, x = 360f, y = 100f, holdX = 380f)

        run(0.6f)

        assertTrue(velocityOf(diver).x < 0f)
        assertEquals(0f, velocityOf(diver).y)
    }

    // endregion

    // region surge

    @Test
    fun `a surging enemy lurches rather than cruising`() {
        val tank = enemy(EnemyMovementType.SURGE, x = 480f, y = 150f, baseSpeedX = -1f)
        val speeds = mutableListOf<Float>()

        repeat(60) {
            world.update(TICK, null)
            speeds += -velocityOf(tank).x
        }

        assertTrue(speeds.max() > 1.2f, "it never surged")
        assertTrue(speeds.min() < 0.5f, "it never eased off")
    }

    // endregion

    // region figure eight

    @Test
    fun `a figure eight boss closes to its station and then circles it`() {
        val boss = enemy(EnemyMovementType.BOSS_FIGURE_EIGHT, x = 480f, y = 120f, holdX = 300f, baseSpeedX = 0f)

        run(8f)
        assertEquals(1, behaviorOf(boss).state)

        val lefts = mutableListOf<Float>()
        repeat(100) {
            run(0.1f)
            lefts += rectOf(boss).left
        }
        assertTrue(lefts.all { it in 240f..360f }, "it wandered off station: ${lefts.min()}..${lefts.max()}")
        assertTrue(lefts.max() - lefts.min() > 30f, "it held still instead of circling")
    }

    // endregion
}
