package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_COLOR
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_INTENSITY
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_RADIUS
import at.smiech.cyanbat.util.CACO_IMP_BREATH
import at.smiech.cyanbat.util.CACO_IMP_DOUSE_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_FLICKER
import at.smiech.cyanbat.util.CACO_IMP_LIGHT_COLOR
import at.smiech.cyanbat.util.CACO_IMP_PROWL_BAT_CLEARANCE
import at.smiech.cyanbat.util.CACO_IMP_PROWL_LEAST_MOVE
import at.smiech.cyanbat.util.CACO_IMP_PROWL_LEFTMOST
import at.smiech.cyanbat.util.CACO_IMP_SMOULDER_INTENSITY
import at.smiech.cyanbat.util.CACO_IMP_SUMMON_SECONDS
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyBehaviorSystem
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthBarComponent
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.LightComponent
import at.smiech.engine.ecs.MovementSystem
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.WeaponSystem
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class ImpSheet(override val width: Int = 384, override val height: Int = 29 * 3) : Pixmap {
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/** The Caco Imp's fight in the dark, run through the same movement and weapon the game runs it by. */
class CacoImpBrainTest {

    private val fired = mutableListOf<EntityId>()
    private val world = World().apply {
        addSystem(MovementSystem())
        addSystem(WeaponSystem { fired += it })
        addSystem(EnemyBehaviorSystem())
    }

    /** Lit, as the cave makes everything that gives off light. */
    private val factory = EntityFactory(world, lit = true)
    private val phases = mutableListOf<Int>()
    private var summons = 0

    private val bar = Rect.fromLTWH(220f, 25f, 200f, 4f)

    private val imp = factory.createBoss(
        x = 397f, y = 136f, holdX = 397f, pixmap = ImpSheet(), scale = 3f,
        hitPoints = 3000, damage = 67, gun = CacoImpBrain.SMOULDERING_GUN, bar = bar,
    )

    /** The bat, low on the left, where the imp has to keep clear of it. */
    private val bat = world.createEntity().also {
        world.addComponent(it, TransformComponent(Rect.fromLTWH(100f, 260f, 45f, 40f)))
        world.addComponent(it, PlayerControlComponent())
        world.addComponent(it, HealthComponent(100))
    }

    private val brain = CacoImpBrain(
        world, imp, FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT,
        random = Random(20261004),
        onSummon = { summons++ },
        onPhaseChanged = { phases += it },
    )

    private val light get() = world.getComponent(imp, LightComponent::class)!!
    private val health get() = world.getComponent(imp, HealthComponent::class)!!
    private val behavior get() = world.getComponent(imp, EnemyBehaviorComponent::class)!!
    private val rect get() = world.getComponent(imp, TransformComponent::class)!!.rect

    /** A tick the way the game runs one: the world, then the stage clock and the boss's brain. */
    private fun tick() {
        world.update(TICK_INITIAL, null)
        brain.update(TICK_INITIAL)
    }

    private fun run(seconds: Float) = repeat((seconds / TICK_INITIAL).toInt()) { tick() }

    /** Runs until [done], failing rather than hanging if it never comes. */
    private fun runUntil(limitSeconds: Float = 20f, done: () -> Boolean) {
        var left = (limitSeconds / TICK_INITIAL).toInt()
        while (!done()) {
            assertTrue(left-- > 0, "it never came round")
            tick()
        }
    }

    private fun woundTo(fraction: Float) {
        health.hitPoints = (health.maxHitPoints * fraction).toInt()
    }

    @Test
    fun `it is alight from the start and smoulders crimson`() {
        repeat(200) {
            tick()
            assertEquals(CACO_IMP_LIGHT_COLOR, light.color)
            assertTrue(light.intensity in CACO_IMP_SMOULDER_INTENSITY - CACO_IMP_BREATH..CACO_IMP_SMOULDER_INTENSITY + CACO_IMP_BREATH)
        }
        assertEquals(1, brain.phase)
        assertEquals(EnemyMovementType.BOSS, behavior.type)
    }

    @Test
    fun `it opens on straight bolts and fires on its own cadence`() {
        assertEquals(CacoImpBrain.SMOULDERING_GUN.volleys, world.getComponent(imp, GunComponent::class)!!.volleys)

        run(CacoImpBrain.SMOULDERING_GUN.interval * 3 + 0.1f)

        assertEquals(3, fired.size)
    }

    /** It goes dark to move, so its bar cannot hang under it: that would show where it had got to. */
    @Test
    fun `its health bar is pinned to the screen`() {
        assertEquals(bar, world.getComponent(imp, HealthBarComponent::class)!!.pinnedTo)
    }

    @Test
    fun `wounded it puts its light out and prowls the dark to somewhere else`() {
        woundTo(0.6f)
        tick()
        assertEquals(listOf(2), phases)

        run(CACO_IMP_DOUSE_SECONDS - 0.05f)
        assertEquals(CacoImpBrain.Prowl.DOUSE, brain.prowl)
        assertTrue(light.intensity < CACO_IMP_SMOULDER_INTENSITY * 0.2f, "its light is not going out")
        val before = rect
        run(0.1f)
        assertEquals(CacoImpBrain.Prowl.PROWL, brain.prowl)
        assertEquals(0f, light.intensity, "a light still on in the dark")
        assertEquals(EnemyMovementType.GLIDE, behavior.type)

        runUntil { brain.prowl == CacoImpBrain.Prowl.LURK }
        assertEquals(0f, light.intensity)
        assertTrue(hypot(rect.left - brain.stationX, rect.top - brain.stationY) < 1f, "it stopped short")
        assertTrue(hypot(rect.left - before.left, rect.top - before.top) >= CACO_IMP_PROWL_LEAST_MOVE - 2f)
    }

    @Test
    fun `it settles clear of the bat and on the side hostiles come from`() {
        woundTo(0.6f)
        tick()
        val batRect = world.getComponent(bat, TransformComponent::class)!!.rect
        repeat(6) {
            runUntil { brain.prowl == CacoImpBrain.Prowl.PROWL }
            val centerX = brain.stationX + rect.width / 2f
            val centerY = brain.stationY + rect.height / 2f
            assertTrue(hypot(centerX - batRect.centerX, centerY - batRect.centerY) >= CACO_IMP_PROWL_BAT_CLEARANCE)
            assertTrue(brain.stationX >= FRAME_BUFFER_WIDTH * CACO_IMP_PROWL_LEFTMOST)
            assertTrue(brain.stationX + rect.width <= FRAME_BUFFER_WIDTH)
            assertTrue(brain.stationY >= 0f && brain.stationY + rect.height <= FRAME_BUFFER_HEIGHT)
            runUntil { brain.prowl == CacoImpBrain.Prowl.BURN }
        }
    }

    /** A bolt is a light: one fired in the dark would give away what the dark is hiding. */
    @Test
    fun `it holds its fire in the dark and ambushes from its flare`() {
        woundTo(0.6f)
        tick()
        fired.clear()

        runUntil { brain.prowl == CacoImpBrain.Prowl.FLARE }
        assertEquals(emptyList(), fired, "it fired in the dark")

        // The flare is the warning, and the light comes up past its smoulder before a bolt flies.
        runUntil { brain.prowl == CacoImpBrain.Prowl.BURN }
        assertTrue(light.intensity > CACO_IMP_SMOULDER_INTENSITY, "no flare to see it by")
        assertEquals(EnemyMovementType.BOSS, behavior.type)

        run(1f)
        assertEquals(2, fired.size, "the ambush is a ring and then a fan")
    }

    @Test
    fun `it goes round again dark and lit for as long as the phase lasts`() {
        woundTo(0.6f)
        tick()
        val seen = mutableListOf<CacoImpBrain.Prowl>()
        repeat((30f / TICK_INITIAL).toInt()) {
            tick()
            if (seen.lastOrNull() != brain.prowl) seen += brain.prowl
        }
        assertTrue(seen.count { it == CacoImpBrain.Prowl.PROWL } >= 3, "it went round only $seen")
        assertTrue(seen.windowed(5).all { it.toSet().size == 5 }, "out of order: $seen")
    }

    @Test
    fun `battered it blazes up for good and calls in its own kind`() {
        woundTo(0.6f)
        tick()
        runUntil { brain.dark }

        woundTo(0.3f)
        tick()
        assertEquals(listOf(2, 3), phases)
        assertEquals(EnemyMovementType.BOSS, behavior.type)
        assertTrue(behavior.tempo > 1f, "ablaze, and no quicker")

        repeat((CACO_IMP_SUMMON_SECONDS * 2f / TICK_INITIAL).toInt()) {
            tick()
            assertEquals(CACO_IMP_BLAZE_COLOR, light.color)
            assertEquals(CACO_IMP_BLAZE_RADIUS, light.radius)
            assertTrue(light.intensity in CACO_IMP_BLAZE_INTENSITY * (1f - CACO_IMP_FLICKER)..CACO_IMP_BLAZE_INTENSITY)
        }
        assertEquals(2, summons)
    }

    @Test
    fun `one blow past both thresholds plays both phases and leaves it alight`() {
        woundTo(0.2f)
        tick()

        assertEquals(listOf(2, 3), phases)
        assertTrue(light.intensity > 0f)
        assertNotNull(world.getComponent(imp, GunComponent::class))
    }

    @Test
    fun `a dead imp does nothing more`() {
        health.alive = false
        health.hitPoints = 0
        run(5f)

        assertEquals(emptyList(), phases)
        assertEquals(0, summons)
    }
}
