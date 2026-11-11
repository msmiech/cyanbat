package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_COLOR
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_INTENSITY
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_RADIUS
import at.smiech.cyanbat.util.CACO_IMP_BREATH
import at.smiech.cyanbat.util.CACO_IMP_BURN_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_CHARGE_REST_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_COLD_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_FLARE_INTENSITY
import at.smiech.cyanbat.util.CACO_IMP_FLICKER
import at.smiech.cyanbat.util.CACO_IMP_LIGHT_COLOR
import at.smiech.cyanbat.util.CACO_IMP_SHIELD_FRACTION
import at.smiech.cyanbat.util.CACO_IMP_SMOULDER_INTENSITY
import at.smiech.cyanbat.util.CACO_IMP_SUMMON_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_WINDUP_SECONDS
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.HIT_FLASH_COLOR
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyBehaviorSystem
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthBarComponent
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.HitFlashComponent
import at.smiech.engine.ecs.LightComponent
import at.smiech.engine.ecs.MovementSystem
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.ShieldComponent
import at.smiech.engine.ecs.TintComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.WeaponSystem
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class ImpSheet(override val width: Int = 384, override val height: Int = 29 * 3) : Pixmap {
    override fun dispose() = Unit
}

/** The Caco Imp's fight, run through the same movement and weapon the game runs it by. */
class CacoImpBrainTest {

    private val fired = mutableListOf<EntityId>()
    private val world = World().apply {
        addSystem(MovementSystem())
        addSystem(WeaponSystem { fired += it })
        addSystem(EnemyBehaviorSystem())
    }

    private val phases = mutableListOf<Int>()
    private var summons = 0

    private val bar = Rect.fromLTWH(220f, 25f, 200f, 4f)

    /** Where it holds station, as the generator puts it. */
    private val holdX = 397f

    /** The imp, lit as the cave makes it unless [lit] is false, as in daylight. */
    private fun spawn(lit: Boolean): EntityId = EntityFactory(world, lit = lit).createBoss(
        x = holdX, y = 136f, holdX = holdX, pixmap = ImpSheet(), scale = 3f,
        hitPoints = 3000, damage = 67, gun = CacoImpBrain.SMOULDERING_GUN, bar = bar,
    )

    private var imp = spawn(lit = true)

    /** The bat, low on the left, where it would be dodging from. */
    private val bat = world.createEntity().also {
        world.addComponent(it, TransformComponent(Rect.fromLTWH(100f, 260f, 45f, 40f)))
        world.addComponent(it, PlayerControlComponent())
        world.addComponent(it, HealthComponent(100))
    }

    private var brain = brainFor(imp)

    private fun brainFor(id: EntityId) = CacoImpBrain(
        world, id, FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT,
        onSummon = { summons++ },
        onPhaseChanged = { phases += it },
    )

    private val light get() = world.getComponent(imp, LightComponent::class)!!
    private val tint get() = world.getComponent(imp, TintComponent::class)!!
    private val shield get() = world.getComponent(imp, ShieldComponent::class)!!
    private val health get() = world.getComponent(imp, HealthComponent::class)!!
    private val behavior get() = world.getComponent(imp, EnemyBehaviorComponent::class)!!
    private val rect get() = world.getComponent(imp, TransformComponent::class)!!.rect
    private val batRect get() = world.getComponent(bat, TransformComponent::class)!!.rect
    private val tintAlpha get() = (tint.color ushr 24) / 255f

    /** A tick the way the game runs one: the world, then the stage clock and the boss's brain. */
    private fun tick() {
        world.update(TICK_INITIAL, null)
        brain.update(TICK_INITIAL)
    }

    private fun run(seconds: Float) = repeat((seconds / TICK_INITIAL).roundToInt()) { tick() }

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
            assertEquals(0, tint.color)
        }
        assertEquals(1, brain.phase)
        assertEquals(EnemyMovementType.BOSS, behavior.type)
    }

    @Test
    fun `it opens on straight bolts and fires on its own cadence, and never charges`() {
        assertEquals(
            CacoImpBrain.SMOULDERING_GUN.volleys,
            world.getComponent(imp, GunComponent::class)!!.volleys
        )

        run(CacoImpBrain.SMOULDERING_GUN.interval * 3 + 0.1f)

        assertEquals(3, fired.size)
        assertEquals(CacoImpBrain.Move.STATION, brain.move)
    }

    /** It charges out of the frame and back, so its bar cannot hang under it. */
    @Test
    fun `its health bar is pinned to the screen`() {
        assertEquals(bar, world.getComponent(imp, HealthBarComponent::class)!!.pinnedTo)
    }

    @Test
    fun `its phases come at fifths of its health, each announced in turn`() {
        for ((fraction, phase) in listOf(0.79f to 2, 0.59f to 3, 0.39f to 4, 0.19f to 5)) {
            woundTo(fraction)
            tick()
            assertEquals(phase, brain.phase)
        }
        assertEquals(listOf(2, 3, 4, 5), phases)
    }

    /**
     * The charge's tell is on its sprite as well as in its light, so it reads by day and in a frame
     * the bat's shots have lit: it rests, rears back burning white-hot and holding its fire, then
     * rushes.
     */
    @Test
    fun `charging, it rests, then winds up white-hot before it rushes`() {
        woundTo(0.7f)
        tick()
        run(CACO_IMP_CHARGE_REST_SECONDS - 0.2f)
        assertEquals(CacoImpBrain.Move.STATION, brain.move, "it charged without a rest")

        runUntil { brain.move == CacoImpBrain.Move.WINDUP }
        fired.clear()
        val start = rect
        // Read just before the rush, as the wind-up peaks.
        run(CACO_IMP_WINDUP_SECONDS * 0.9f)
        assertTrue(tintAlpha > 0.3f, "no white-hot wash to see the wind-up by")
        assertTrue(light.intensity > CACO_IMP_SMOULDER_INTENSITY + CACO_IMP_BREATH, "no flare")
        runUntil { brain.move == CacoImpBrain.Move.CHARGE }
        assertEquals(CACO_IMP_FLARE_INTENSITY, light.intensity, 0.01f)
        assertEquals(emptyList(), fired, "it fired through its wind-up")
        assertTrue(rect.left > start.left, "it did not rear back")
    }

    /** Under fire its hit flash would hide the wind-up's wash, so the two take turns. */
    @Test
    fun `no hit flashes it through a wind-up and a rush, and they come back after`() {
        woundTo(0.7f)
        tick()
        runUntil { brain.move == CacoImpBrain.Move.WINDUP }
        assertEquals(0, world.getComponent(imp, HitFlashComponent::class)!!.color)
        runUntil { brain.move == CacoImpBrain.Move.RETURN }
        assertEquals(HIT_FLASH_COLOR, world.getComponent(imp, HitFlashComponent::class)!!.color)
    }

    @Test
    fun `the rush is aimed once, leaves the frame and comes back in from the right`() {
        woundTo(0.7f)
        tick()
        runUntil { brain.move == CacoImpBrain.Move.CHARGE }
        val aim = behavior.holdX to behavior.initialY

        // The bat moving off does not steer it.
        world.getComponent(bat, TransformComponent::class)!!.rect =
            Rect.fromLTWH(100f, 20f, 45f, 40f)
        fired.clear()
        runUntil { brain.move != CacoImpBrain.Move.CHARGE }
        assertEquals(CacoImpBrain.Move.RETURN, brain.move)
        assertEquals(emptyList(), fired, "it fired mid-rush")
        assertTrue(rect.left >= FRAME_BUFFER_WIDTH, "it came back somewhere other than the right")

        runUntil { brain.move == CacoImpBrain.Move.STATION }
        assertEquals(holdX, rect.left, 1.5f)
        assertEquals(EnemyMovementType.BOSS, behavior.type)
        assertTrue(aim.first < 0f, "aimed $aim, not off to the left")
    }

    @Test
    fun `mid-rush, its middle runs along the line through the bat`() {
        woundTo(0.7f)
        tick()
        runUntil { brain.move == CacoImpBrain.Move.CHARGE }
        val target = batRect
        val from = rect
        val dx = target.centerX - from.centerX
        val dy = target.centerY - from.centerY
        var nearest = Float.MAX_VALUE
        while (brain.move == CacoImpBrain.Move.CHARGE) {
            tick()
            // The tick it leaves the frame, it is put back at the right edge.
            if (brain.move != CacoImpBrain.Move.CHARGE) break
            // How far its middle is from the line, perpendicular.
            val off = abs((rect.centerX - from.centerX) * dy - (rect.centerY - from.centerY) * dx) /
                    hypot(dx, dy)
            assertTrue(off < 2f, "it strayed $off off its line")
            nearest = minOf(
                nearest,
                hypot(rect.centerX - target.centerX, rect.centerY - target.centerY)
            )
        }
        assertTrue(nearest < 8f, "it passed $nearest from where the bat was")
    }

    @Test
    fun `spitting embers, it stops charging`() {
        woundTo(0.5f)
        tick()
        assertEquals(3, brain.phase)
        assertTrue(
            world.getComponent(imp, GunComponent::class)!!.volleys
                .any { it.pattern == ShotPattern.EMBER },
            "no embers in its gun"
        )
        repeat((15f / TICK_INITIAL).toInt()) {
            tick()
            assertEquals(CacoImpBrain.Move.STATION, brain.move)
        }
    }

    @Test
    fun `burning, it fights inside a fire shield it can be shot out of`() {
        woundTo(0.3f)
        tick()
        assertEquals(4, brain.phase)
        assertTrue(brain.burning)
        assertEquals((health.maxHitPoints * CACO_IMP_SHIELD_FRACTION).roundToInt(), shield.points)
        assertEquals(CACO_IMP_BLAZE_COLOR, light.color)
        run(2f)
        assertTrue(fired.isNotEmpty(), "it held its fire burning")
        assertEquals(CacoImpBrain.Move.STATION, brain.move, "it charged while burning")

        // Shot down, the shield takes the fire with it.
        shield.absorb(shield.points)
        tick()
        assertFalse(brain.burning)
        assertEquals(0f, light.intensity)
        assertTrue(tintAlpha > 0.3f, "nothing on its sprite to say it has gone cold")
    }

    @Test
    fun `cold, it holds its fire and charges, then burns again`() {
        woundTo(0.3f)
        tick()
        shield.absorb(shield.points)
        tick()
        fired.clear()

        runUntil { brain.move == CacoImpBrain.Move.CHARGE }
        assertEquals(emptyList(), fired, "it fired cold")
        assertFalse(shield.isUp, "a shield up while it is cold")

        runUntil(CACO_IMP_COLD_SECONDS + 10f) { brain.burning }
        assertEquals(CacoImpBrain.Move.STATION, brain.move, "it lit up mid-charge")
        assertTrue(shield.isUp, "it lit up without its shield")
        assertTrue(light.intensity > 0f)
    }

    @Test
    fun `a shield left alone burns out, and it goes cold anyway`() {
        woundTo(0.3f)
        tick()
        run(CACO_IMP_BURN_SECONDS - 0.2f)
        assertTrue(brain.burning)
        run(0.4f)
        assertFalse(brain.burning)
        assertFalse(shield.isUp)
    }

    @Test
    fun `ablaze, it lights up for good, charges and calls in its own kind`() {
        woundTo(0.3f)
        tick()
        woundTo(0.1f)
        tick()
        assertEquals(listOf(2, 3, 4, 5), phases)
        assertFalse(shield.isUp, "it carried its shield into the blaze")
        assertTrue(behavior.tempo > 1f, "ablaze, and no quicker")

        var charged = false
        repeat((CACO_IMP_SUMMON_SECONDS * 2f / TICK_INITIAL).toInt()) {
            tick()
            if (brain.move == CacoImpBrain.Move.CHARGE) charged = true
            assertEquals(CACO_IMP_BLAZE_COLOR, light.color)
            assertEquals(CACO_IMP_BLAZE_RADIUS, light.radius)
            if (brain.move == CacoImpBrain.Move.STATION) {
                val dimmest = CACO_IMP_BLAZE_INTENSITY * (1f - CACO_IMP_FLICKER)
                assertTrue(light.intensity in dimmest..CACO_IMP_BLAZE_INTENSITY)
            }
        }
        assertEquals(2, summons)
        assertTrue(charged, "it never charged ablaze")
    }

    /** A light's sprite is cached by its radius and color, so the fight moves only its strength. */
    @Test
    fun `its light only ever takes two sizes`() {
        val radii = mutableSetOf<Int>()
        for (fraction in listOf(0.9f, 0.7f, 0.5f, 0.3f, 0.1f)) {
            woundTo(fraction)
            repeat((8f / TICK_INITIAL).toInt()) {
                tick()
                radii += light.radius
            }
        }
        assertEquals(2, radii.size, "radii $radii")
    }

    /** By day there is no light to read it by, so everything it does must still show and work. */
    @Test
    fun `in daylight it fights the same fight, its tells on its sprite`() {
        world.removeEntity(imp)
        imp = spawn(lit = false)
        brain = brainFor(imp)
        assertNull(world.getComponent(imp, LightComponent::class))

        woundTo(0.7f)
        tick()
        runUntil { brain.move == CacoImpBrain.Move.WINDUP }
        run(CACO_IMP_WINDUP_SECONDS * 0.9f)
        assertTrue(tintAlpha > 0.3f, "no wind-up to see by day")

        woundTo(0.3f)
        runUntil { brain.burning }
        assertTrue(shield.isUp)
        shield.absorb(shield.points)
        tick()
        assertFalse(brain.burning)
        assertTrue(tintAlpha > 0.3f, "no sign by day that it has gone cold")
    }

    @Test
    fun `one blow past every threshold plays every phase and leaves it alight`() {
        woundTo(0.1f)
        tick()

        assertEquals(listOf(2, 3, 4, 5), phases)
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
