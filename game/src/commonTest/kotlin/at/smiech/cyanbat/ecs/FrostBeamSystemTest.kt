package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.util.FROST_BEAM_SHOW_SECONDS
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.PaceComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How the frost beam's rays are traced across the frame, and what they catch. */
class FrostBeamSystemTest {

    private var bat: Rect? = Rect.fromLTWH(78f, 160f, 45f, 40f)
    private val exempt = mutableSetOf<EntityId>()
    private val shots = mutableListOf<List<EntityId>>()

    private fun harness(seed: Int = 1): Pair<World, FrostBeamSystem> {
        val beam = FrostBeamSystem(
            WIDTH,
            HEIGHT,
            Random(seed),
            origin = { bat },
            canFreeze = { it !in exempt },
            onFire = { frozen, _ -> shots += frozen },
        )
        beam.intervalSeconds = INTERVAL
        beam.freezeSeconds = 2f
        return World().apply { addSystem(beam) } to beam
    }

    /** An enemy, 32x29, centered on ([x], [y]). */
    private fun World.enemy(x: Float, y: Float): EntityId {
        val id = createEntity()
        addComponent(id, TransformComponent(Rect.fromLTWH(x - 16f, y - 14.5f, 32f, 29f)))
        addComponent(id, CollisionComponent(5f, CollisionGroup.ENEMY))
        addComponent(id, HealthComponent(100))
        addComponent(id, PaceComponent())
        return id
    }

    private fun World.step(seconds: Float) {
        var left = seconds
        while (left > 0f) {
            update(TICK, null)
            left -= TICK
        }
    }

    private fun World.frozen(id: EntityId) = FrostSystem.isFrozen(this, id)

    // region where it goes

    @Test
    fun `a ray runs on to the edge it is pointed at`() {
        assertEquals(640f to 180f, FrostBeamSystem.rayToEdge(100f, 180f, 300f, 180f, WIDTH, HEIGHT))
        assertEquals(0f to 180f, FrostBeamSystem.rayToEdge(100f, 180f, 50f, 180f, WIDTH, HEIGHT))
        // Up and to the right at 45 degrees: out through the top, 180 across.
        assertEquals(280f to 0f, FrostBeamSystem.rayToEdge(100f, 180f, 150f, 130f, WIDTH, HEIGHT))
    }

    /** Aimed through something past the edge, it still reaches that far. */
    @Test
    fun `a ray never stops short of what it was aimed through`() {
        assertEquals(660f to 180f, FrostBeamSystem.rayToEdge(100f, 180f, 660f, 180f, WIDTH, HEIGHT))
    }

    @Test
    fun `a segment is clipped to a box it crosses and misses one it does not`() {
        val box = Rect.fromLTWH(40f, 0f, 20f, 100f)

        val crossing = assertNotNull(FrostBeamSystem.clipSegment(0f, 50f, 100f, 50f, box))
        assertEquals(0.4f, crossing.start, 1e-5f)
        assertEquals(0.6f, crossing.endInclusive, 1e-5f)

        assertNull(FrostBeamSystem.clipSegment(0f, 150f, 100f, 150f, box), "passes under it")
        assertNull(FrostBeamSystem.clipSegment(0f, 50f, 30f, 50f, box), "stops short of it")
        val inside =
            assertNotNull(FrostBeamSystem.clipSegment(50f, 50f, 100f, 50f, box), "starts inside it")
        assertEquals(0f, inside.start)
    }

    // endregion

    @Test
    fun `there is no beam until one has been bought`() {
        val (world, beam) = harness()
        beam.intervalSeconds = 0f
        val enemy = world.enemy(300f, 180f)

        world.step(10f)

        assertTrue(shots.isEmpty())
        assertFalse(world.frozen(enemy))
    }

    @Test
    fun `it goes off once its interval is up and freezes what it was aimed at`() {
        val (world, _) = harness()
        val enemy = world.enemy(300f, 100f)

        world.step(INTERVAL - 0.05f)
        assertTrue(shots.isEmpty(), "went off early")

        world.step(0.1f)
        assertEquals(listOf(listOf(enemy)), shots)
        assertTrue(world.frozen(enemy))
    }

    /**
     * Every enemy along the line freezes, the ones past the target as well, and one only partly in
     * the frame too. One off the line does not; nor does one it may not freeze, which it goes
     * through as if it were not there.
     */
    @Test
    fun `it freezes every enemy it may along its line and nothing else`() {
        val (world, _) = harness()
        val near = world.enemy(300f, 180f)
        val far = world.enemy(500f, 182f)
        val edge = world.enemy(645f, 178f)
        val elite = world.enemy(400f, 180f).also { exempt += it }
        val above = world.enemy(645f, 60f)

        world.step(INTERVAL + TICK)

        assertTrue(
            world.frozen(near) && world.frozen(far) && world.frozen(edge),
            "the line is not all frozen"
        )
        assertFalse(world.frozen(elite), "it froze something it may not")
        assertFalse(world.frozen(above), "it froze something off its line")
        assertTrue(
            shots.single().first() in setOf(near, far),
            "it aimed at something it could not see whole"
        )
    }

    /** Never at a boss or an elite: with nothing else on screen it does not go off at all. */
    @Test
    fun `it never aims at what it may not freeze`() {
        val (world, _) = harness()
        val boss = world.enemy(400f, 180f).also { exempt += it }

        world.step(INTERVAL * 3)

        assertTrue(shots.isEmpty())
        assertFalse(world.frozen(boss))
    }

    /** Come round on an empty screen, it goes off at the first enemy in, without another wait. */
    @Test
    fun `its charge holds until there is something to aim at`() {
        val (world, _) = harness()
        world.step(INTERVAL * 2)
        assertTrue(shots.isEmpty())

        val enemy = world.enemy(500f, 300f)
        world.step(TICK)

        assertEquals(listOf(listOf(enemy)), shots)
    }

    @Test
    fun `it goes for what is not frozen yet`() {
        repeat(20) { seed ->
            shots.clear()
            val (world, _) = harness(seed)
            val already = world.enemy(300f, 60f)
            FrostSystem.freeze(world, already, 10f)
            val fresh = world.enemy(300f, 300f)

            world.step(INTERVAL + TICK)

            assertEquals(
                fresh,
                shots.single().first(),
                "seed $seed aimed at the one already frozen"
            )
        }
    }

    @Test
    fun `with no bat there is no beam`() {
        val (world, _) = harness()
        val enemy = world.enemy(300f, 180f)
        bat = null

        world.step(INTERVAL * 2)

        assertFalse(world.frozen(enemy))
    }

    /** Shown from the bat's edge to the frame's, for a moment, and then gone. */
    @Test
    fun `the beam shows from the bat's edge for a moment`() {
        val (world, _) = harness()
        world.enemy(300f, 180f)
        world.step(INTERVAL + TICK)

        val shown = world.query(FrostBeamComponent::class).single()
        val beam = world.getComponent(shown, FrostBeamComponent::class)!!
        assertEquals(bat!!.right, beam.fromX, 0.01f)
        assertEquals(180f, beam.fromY, 0.01f)
        assertEquals(640f, beam.toX, 0.01f)

        world.step(FROST_BEAM_SHOW_SECONDS + TICK)
        assertTrue(world.query(FrostBeamComponent::class).isEmpty(), "the beam stayed up")
    }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 360
        const val TICK = 0.019f
        const val INTERVAL = 1f
    }
}
