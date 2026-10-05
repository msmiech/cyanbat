package at.smiech.engine.ecs

import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TICK = 0.019f

/**
 * The jungle's and the desert's movement patterns, and the bosses' glide between stations. Each
 * test drives a pattern the way the game does - behavior,
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
                type,
                laneY,
                holdX = holdX,
                baseSpeedX = baseSpeedX,
                offsetY = offsetY,
                phase = phase,
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
    private fun velocityOf(id: EntityId) =
        world.getComponent(id, VelocityComponent::class)!!.velocity

    private fun behaviorOf(id: EntityId) = world.getComponent(id, EnemyBehaviorComponent::class)!!

    private fun run(seconds: Float) = repeat((seconds / TICK).toInt()) { world.update(TICK, null) }

    // region formation

    /** Nobody steers by anybody else, so the shape has to survive on shared maths alone. */
    @Test
    fun `a formation keeps its shape while it flies`() {
        val leader = enemy(EnemyMovementType.FORMATION, x = 480f, y = 150f)
        val wing =
            enemy(EnemyMovementType.FORMATION, x = 502f, y = 167f, laneY = 150f, offsetY = 17f)

        repeat(6) {
            run(0.5f)
            assertEquals(
                22f,
                rectOf(wing).left - rectOf(leader).left,
                0.01f,
                "the ranks drifted apart"
            )
            assertEquals(
                17f,
                rectOf(wing).top - rectOf(leader).top,
                0.5f,
                "the wing left its place"
            )
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
        val a = enemy(
            EnemyMovementType.SWARM,
            x = 480f,
            y = 140f,
            laneY = 150f,
            offsetY = -10f,
            phase = 0f
        )
        val b = enemy(
            EnemyMovementType.SWARM,
            x = 490f,
            y = 170f,
            laneY = 150f,
            offsetY = 20f,
            phase = 2f
        )

        run(3f)

        // Each buzzes by a few pixels about its own place; the places stay 30 apart.
        val gap = rectOf(b).top - rectOf(a).top
        assertTrue(abs(gap - 30f) < 16f, "the swarm came apart: gap $gap")
    }

    // endregion

    // region hover

    @Test
    fun `a hoverer stops on station and then leaves`() {
        val hoverer = enemy(EnemyMovementType.HOVER, x = 480f, y = 150f, holdX = 360f)

        run(4f)
        val onStation = rectOf(hoverer).left
        assertTrue(
            onStation <= 360f && onStation > 340f,
            "it did not stop at its station: $onStation"
        )
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

        assertTrue(
            rectOf(hoverer).top < 160f,
            "it stayed in its own lane at ${rectOf(hoverer).top}"
        )
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
    fun `a diver commits to where the player was and flies straight`() {
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
        val boss = enemy(
            EnemyMovementType.BOSS_FIGURE_EIGHT,
            x = 480f,
            y = 120f,
            holdX = 300f,
            baseSpeedX = 0f
        )

        run(8f)
        assertEquals(1, behaviorOf(boss).state)

        val lefts = mutableListOf<Float>()
        repeat(100) {
            run(0.1f)
            lefts += rectOf(boss).left
        }
        assertTrue(
            lefts.all { it in 240f..360f },
            "it wandered off station: ${lefts.min()}..${lefts.max()}"
        )
        assertTrue(lefts.max() - lefts.min() > 30f, "it held still instead of circling")
    }

    // endregion

    // region leap

    /** Just under the bottom of a 320 high frame, with its back showing above the sand. */
    private fun leaper(holdX: Float = 300f) =
        enemy(EnemyMovementType.LEAP, x = 480f, y = 310f, holdX = holdX, baseSpeedX = -1.6f)

    /** The back cutting through the sand is the warning, so it has to stay at one height. */
    @Test
    fun `a leaper cruises in along the sand before it leaps`() {
        val leaper = leaper()

        run(1.5f)

        assertEquals(310f, rectOf(leaper).top, 0.5f, "it left the sand before its station")
        assertTrue(rectOf(leaper).left < 480f, "it never came in")
        assertEquals(0, behaviorOf(leaper).state)
    }

    @Test
    fun `a leap tops out at the player's height and comes back down`() {
        player(x = 60f, y = 130f) // centered at 150
        val leaper = leaper()
        val centers = mutableListOf<Float>()

        repeat((6f / TICK).toInt()) {
            world.update(TICK, null)
            centers += rectOf(leaper).centerY
        }

        assertEquals(150f, centers.min(), 8f, "the apex missed the player's height")
        assertTrue(centers.last() > 330f, "it never fell back into the sand: ${centers.last()}")
    }

    /** Aimed once, like a dive: the arc is thrown, not steered. */
    @Test
    fun `a leap is not steered once it is thrown`() {
        val player = player(x = 60f, y = 130f)
        val leaper = leaper(holdX = 490f)
        run(0.1f)
        val launched = velocityOf(leaper)

        world.getComponent(player, TransformComponent::class)!!.rect =
            Rect.fromLTWH(60f, 20f, 45f, 40f)
        world.update(TICK, null)

        assertEquals(launched.x, velocityOf(leaper).x, "a thrown leap changed course")
        assertTrue(velocityOf(leaper).y > launched.y, "gravity is not pulling it back")
    }

    @Test
    fun `a leap never reaches past the top of the frame`() {
        player(x = 60f, y = -30f)
        val leaper = leaper()
        var highest = Float.MAX_VALUE

        repeat((6f / TICK).toInt()) {
            world.update(TICK, null)
            highest = minOf(highest, rectOf(leaper).top)
        }

        assertTrue(highest >= 40f, "it cleared the top of the frame: $highest")
    }

    /**
     * One coming in from behind cruises right along the sand - past its station would be leaping
     * before it got there - and leaps once it has come as far right as its station, forward.
     */
    @Test
    fun `a leaper from behind cruises right to its station and leaps forward from it`() {
        player(x = 260f, y = 130f)
        val leaper =
            enemy(EnemyMovementType.LEAP, x = -30f, y = 310f, holdX = 120f, baseSpeedX = 1.6f)

        run(1.5f)
        assertEquals(310f, rectOf(leaper).top, 0.5f, "it left the sand before its station")
        assertTrue(rectOf(leaper).left > -30f, "it never came in")
        assertEquals(0, behaviorOf(leaper).state)

        run(3f)
        assertTrue(behaviorOf(leaper).state != 0, "it never leapt")
        assertTrue(velocityOf(leaper).x > 0f, "it leapt backwards")
    }

    // endregion

    // region loop

    @Test
    fun `a looper comes out of its loop where it went in and flies on`() {
        val looper =
            enemy(EnemyMovementType.LOOP, x = 330f, y = 150f, holdX = 320f, baseSpeedX = -1.7f)
        run(0.2f)
        assertEquals(1, behaviorOf(looper).state, "it never started its loop")
        val entry = rectOf(looper)
        val tops = mutableListOf<Float>()

        repeat((1.8f / TICK).toInt()) {
            world.update(TICK, null)
            tops += rectOf(looper).top
        }

        assertTrue(entry.top - tops.min() > 60f, "it never climbed over the top of a loop")
        assertEquals(entry.top, rectOf(looper).top, 6f, "it came out of the loop on another line")
        assertEquals(entry.left, rectOf(looper).left, 6f, "it came out of the loop somewhere else")

        run(0.5f)
        assertEquals(2, behaviorOf(looper).state)
        assertTrue(rectOf(looper).left < entry.left - 40f, "it did not carry on out")
    }

    // endregion

    // region glide

    /** Toward its station in a straight line, whichever way that is - right and up included. */
    @Test
    fun `a glider flies straight to its station and settles there`() {
        val glider = enemy(
            EnemyMovementType.GLIDE,
            x = 300f,
            y = 200f,
            laneY = 80f,
            holdX = 460f,
            baseSpeedX = 1.7f
        )
        val path = mutableListOf<Rect>()

        repeat((6f / TICK).toInt()) {
            world.update(TICK, null)
            path += rectOf(glider)
        }

        assertEquals(460f, rectOf(glider).left, 0.01f)
        assertEquals(80f, rectOf(glider).top, 0.01f)
        assertEquals(1, behaviorOf(glider).state, "it never said it had arrived")
        assertEquals(Vector2.Zero, velocityOf(glider))
        // On the line between where it set out and where it was sent: 160 across for every 120 up.
        path.forEach { assertEquals(200f - (it.left - 300f) * 0.75f, it.top, 0.5f) }
    }

    @Test
    fun `a glider goes no faster than its speed and eases in at the end`() {
        val glider =
            enemy(EnemyMovementType.GLIDE, x = 500f, y = 150f, holdX = 200f, baseSpeedX = 1.7f)
        val steps = mutableListOf<Float>()

        repeat((5f / TICK).toInt()) {
            val before = rectOf(glider).left
            world.update(TICK, null)
            steps += before - rectOf(glider).left
        }

        assertTrue(steps.all { it <= 1.7f + 1e-4f }, "faster than it was sent")
        assertEquals(1.7f, steps.first(), 1e-4f)
        val arriving = steps.filter { it > 0f }.takeLast(10)
        assertTrue(
            arriving.zipWithNext().all { (a, b) -> b <= a + 1e-4f },
            "it did not ease in: $arriving"
        )
        assertTrue(arriving.last() < 1f, "it stopped dead")
    }

    // endregion
}
