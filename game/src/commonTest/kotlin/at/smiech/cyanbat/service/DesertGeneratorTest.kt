package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.BossPartComponent
import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.util.BURROW_SHOWING
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.cyanbat.util.SAND_WYRM_FRAME
import at.smiech.cyanbat.util.SAND_WYRM_PLUME_FRAME
import at.smiech.cyanbat.util.SAND_WYRM_SPACING
import at.smiech.cyanbat.util.SHIELD_REGROWTH_DELAY_SECONDS
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyBehaviorSystem
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.FacesVelocityComponent
import at.smiech.engine.ecs.HealthBarComponent
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.MovementSystem
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.ShieldComponent
import at.smiech.engine.ecs.ShieldSystem
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.WeaponSystem
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class DesertSheet(override val width: Int, override val height: Int) : Pixmap {
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

private const val WIDTH = FRAME_BUFFER_WIDTH
private const val HEIGHT = FRAME_BUFFER_HEIGHT

/**
 * How the generator turns the desert's designs into things that come up out of the sand, and how
 * the Sand Wyrm fights. The world runs the engine's own movement, as the game's does, because a
 * leap and a breach are nothing but movement.
 */
class DesertGeneratorTest {

    private val fired = mutableListOf<EntityId>()
    private val world = World().apply {
        addSystem(MovementSystem())
        addSystem(WeaponSystem { fired += it })
        addSystem(EnemyBehaviorSystem())
        addSystem(ShieldSystem())
    }
    private val factory = EntityFactory(world)
    private val bossPhases = mutableListOf<Int>()
    private val bossSheet = DesertSheet(SAND_WYRM_FRAME * 10, SAND_WYRM_FRAME)

    private fun only(species: EnemySpecies) = StageProgression(
        design = StageDesign(listOf(WaveDesign(listOf(species))), BossKind.SAND_WYRM),
        difficulty = 1.7f,
    )

    private fun generator(progression: StageProgression) = EnemyGenerator(
        xSpawnPosition = WIDTH,
        worldHeight = HEIGHT,
        factory = factory,
        enemyPixmap = DesertSheet(640, 29),
        progression = progression,
        random = Random(20260926),
        bossPixmap = bossSheet,
        onBossPhaseChanged = { bossPhases += it },
    )

    /** One tick in the game's order: the world moves, then the stage clock runs. */
    private fun EnemyGenerator.tick() {
        world.update(TICK_INITIAL, null)
        update(TICK_INITIAL)
    }

    private fun EnemyGenerator.run(seconds: Float) =
        repeat((seconds / TICK_INITIAL).toInt()) { tick() }

    private fun EnemyGenerator.firstArrival() {
        while (enemies().isEmpty()) tick()
    }

    private fun enemies(): List<EntityId> = world.query(EnemyBehaviorComponent::class)
    private fun rectOf(id: EntityId) = world.getComponent(id, TransformComponent::class)!!.rect

    private fun bat(x: Float = 120f, y: Float = 140f): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, 45f, 40f)))
        world.addComponent(id, PlayerControlComponent())
        world.addComponent(id, HealthComponent(100))
        return id
    }

    // region the desert's hostiles

    @Test
    fun `wyrmlings come in along the bottom edge with only their backs showing`() {
        val generator = generator(only(EnemySpecies.WYRMLING))
        generator.firstArrival()
        val wyrmling = enemies().single()

        assertEquals(HEIGHT - BURROW_SHOWING, rectOf(wyrmling).top, 0.5f)
        assertEquals(
            EnemyMovementType.LEAP,
            world.getComponent(wyrmling, EnemyBehaviorComponent::class)!!.type
        )

        // Still cruising a second later, at the same height: the back in the sand is the warning.
        generator.run(0.5f)
        assertEquals(HEIGHT - BURROW_SHOWING, rectOf(wyrmling).top, 1f)
    }

    @Test
    fun `a wyrmling leaps from its station and comes back down into the sand`() {
        bat(y = 120f)
        val generator = generator(only(EnemySpecies.WYRMLING))
        generator.firstArrival()
        val wyrmling = enemies().first()
        var highest = Float.MAX_VALUE
        repeat((7f / TICK_INITIAL).toInt()) {
            generator.tick()
            if (world.getComponent(wyrmling, TransformComponent::class) != null) highest =
                minOf(highest, rectOf(wyrmling).top)
        }

        assertTrue(highest < 170f, "it never leapt up to the bat: its top got to $highest")
    }

    @Test
    fun `the ones that fly arcs are turned to face along them and nothing else is`() {
        for ((species, faces) in listOf(
            EnemySpecies.HAWK to true,
            EnemySpecies.WYRMLING to true,
            EnemySpecies.DJINN to false
        )) {
            val generator = generator(only(species))
            generator.firstArrival()
            val id = enemies().last()
            val facing = world.getComponent(id, FacesVelocityComponent::class)
            if (faces) assertEquals(
                180f,
                assertNotNull(facing, "$species does not turn").artworkDegrees
            )
            else assertNull(facing, "$species turns")
            enemies().forEach { world.removeEntity(it) }
            world.update(TICK_INITIAL, null)
        }
    }

    @Test
    fun `a scarab's shell grows back if it is left alone and only then`() {
        val generator = generator(only(EnemySpecies.SCARAB))
        generator.firstArrival()
        val shield = world.getComponent(enemies().single(), ShieldComponent::class)!!
        assertTrue(shield.isUp)

        shield.absorb(shield.maxPoints)
        assertTrue(!shield.isUp)
        repeat(((SHIELD_REGROWTH_DELAY_SECONDS - 0.5f) / TICK_INITIAL).toInt()) {
            world.update(
                TICK_INITIAL,
                null
            )
        }
        assertTrue(!shield.isUp, "it grew back before it was left alone long enough")

        repeat((3f / TICK_INITIAL).toInt()) { world.update(TICK_INITIAL, null) }
        assertTrue(shield.isUp, "it never grew back")
    }

    // endregion

    // region the Sand Wyrm

    private fun wyrmFight(): Pair<EnemyGenerator, EntityId> {
        val generator = generator(StageProgression.forStage(3))
        generator.run(5 * WAVE_DURATION_SECONDS + 0.1f)
        enemies().filter { it != generator.bossId }.forEach { world.removeEntity(it) }
        world.update(TICK_INITIAL, null)
        return generator to generator.bossId!!
    }

    private fun parts(): List<EntityId> =
        world.query(BossPartComponent::class)
            .filter { !world.hasComponent(it, HealthComponent::class) }

    @Test
    fun `the desert ends on the Sand Wyrm with a head and nine plates all under the sand`() {
        val (_, head) = wyrmFight()

        assertEquals(9, parts().size)
        assertTrue(
            world.hasComponent(head, BossPartComponent::class),
            "the head is not armored against ramming"
        )
        assertNotNull(
            world.getComponent(head, HealthBarComponent::class)?.pinnedTo,
            "its bar goes where it goes"
        )
        (parts() + head).forEach {
            assertTrue(
                rectOf(it).top > HEIGHT,
                "a part of it showed before it breached"
            )
        }
    }

    @Test
    fun `the sand boils where it will come up before it breaches there`() {
        bat(x = 120f)
        val (generator, head) = wyrmFight()
        val brain = generator.bossBrain as SandWyrmBrain

        while (brain.breachX.isNaN()) generator.tick()
        val breachX = brain.breachX
        generator.run(0.5f)
        val plumes = world.query(SpriteComponent::class).filter {
            world.getComponent(
                it,
                SpriteComponent::class
            )!!.baseSrcX == SAND_WYRM_PLUME_FRAME * SAND_WYRM_FRAME
        }
        assertTrue(plumes.isNotEmpty(), "no sand was thrown up before the breach")
        plumes.forEach { assertEquals(breachX, rectOf(it).centerX, 1f) }

        // Ahead of the bat, so the top of the arc comes down on it.
        assertTrue(breachX > 120f + 45f, "it came up behind the bat at $breachX")

        while (brain.burrowed) generator.tick()
        generator.run(1f)
        assertTrue(rectOf(head).bottom < HEIGHT, "it never came up")
    }

    /** It shows the player how it moves before it hunts them. */
    @Test
    fun `its first breach goes by in front of the bat and the next comes at it`() {
        val batId = bat(x = 100f, y = 140f)
        val (generator, head) = wyrmFight()
        val brain = generator.bossBrain as SandWyrmBrain
        val bat = rectOf(batId)

        fun nearestPass(): Float {
            while (brain.burrowed) generator.tick()
            var nearest = Float.MAX_VALUE
            while (!brain.burrowed) {
                generator.tick()
                val rect = rectOf(head)
                nearest =
                    minOf(nearest, hypot(rect.centerX - bat.centerX, rect.centerY - bat.centerY))
            }
            return nearest
        }

        assertTrue(nearestPass() > SAND_WYRM_FRAME + 20f, "the warning came at the bat")
        assertTrue(
            nearestPass() < SAND_WYRM_FRAME / 2f,
            "the hunt went wide of a bat that never moved"
        )
    }

    /** By distance along the path, so the body never bunches or strings out. */
    @Test
    fun `the body follows the head's path a plate's spacing apart`() {
        bat()
        val (generator, head) = wyrmFight()
        val brain = generator.bossBrain as SandWyrmBrain
        while (brain.burrowed) generator.tick()
        generator.run(1.2f)

        val chain = listOf(head) + parts().sortedBy {
            hypot(
                rectOf(it).centerX - rectOf(head).centerX,
                rectOf(it).centerY - rectOf(head).centerY
            )
        }
        chain.zipWithNext { front, back ->
            val gap = hypot(
                rectOf(front).centerX - rectOf(back).centerX,
                rectOf(front).centerY - rectOf(back).centerY
            )
            assertTrue(gap <= SAND_WYRM_SPACING + 0.5f, "two parts came apart: $gap")
            assertTrue(gap >= SAND_WYRM_SPACING * 0.5f, "two parts bunched up: $gap")
        }
    }

    @Test
    fun `it spits at the top of every arc and goes back under`() {
        bat(y = 140f)
        val (generator, head) = wyrmFight()
        val brain = generator.bossBrain as SandWyrmBrain

        while (brain.burrowed) generator.tick()
        while (!brain.burrowed) generator.tick()

        assertTrue(head in fired, "it never spat")
        (parts() + head).forEach {
            assertTrue(
                rectOf(it).top > HEIGHT,
                "part of it stayed out of the sand"
            )
        }
    }

    @Test
    fun `wounding it into phase two rings its spit and calls up its brood`() {
        bat()
        val (generator, head) = wyrmFight()
        val health = world.getComponent(head, HealthComponent::class)!!

        health.hitPoints = (health.maxHitPoints * 0.6f).toInt()
        generator.tick()
        assertEquals(listOf(2), bossPhases)
        assertTrue(
            world.getComponent(
                head,
                GunComponent::class
            )!!.volleys.any { it.pattern == ShotPattern.RADIAL })

        val brain = generator.bossBrain as SandWyrmBrain
        while (brain.burrowed) generator.tick()
        val brood = enemies().filter {
            world.getComponent(
                it,
                EnemyBehaviorComponent::class
            )?.type == EnemyMovementType.LEAP && it != head
        }
        assertEquals(1, brood.size, "a breach in phase two called up ${brood.size} wyrmlings")
    }

    @Test
    fun `one blow past both thresholds still plays both phases in order`() {
        val (generator, head) = wyrmFight()
        val health = world.getComponent(head, HealthComponent::class)!!

        health.hitPoints = (health.maxHitPoints * 0.2f).toInt()
        generator.tick()

        assertEquals(listOf(2, 3), bossPhases)
    }

    // endregion
}
