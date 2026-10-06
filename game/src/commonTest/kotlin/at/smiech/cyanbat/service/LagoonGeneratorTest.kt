package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.BossPartComponent
import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.util.BURROW_SHOWING
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.cyanbat.util.NAGA_FRAME
import at.smiech.cyanbat.util.NAGA_SPACING_MOST
import at.smiech.cyanbat.util.NAGA_SPLASH_FRAME
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyBehaviorSystem
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
import at.smiech.engine.ecs.VelocityComponent
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

private class LagoonSheet(override val width: Int, override val height: Int) : Pixmap {
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

private const val WIDTH = FRAME_BUFFER_WIDTH
private const val HEIGHT = FRAME_BUFFER_HEIGHT

/**
 * How the generator turns the lagoon's designs into things in the water, and how the Naga fights.
 * The world runs the engine's own movement, as the game's does, because a shark's leap is nothing
 * but movement.
 */
class LagoonGeneratorTest {

    private val fired = mutableListOf<EntityId>()
    private val world = World().apply {
        addSystem(MovementSystem())
        addSystem(WeaponSystem { fired += it })
        addSystem(EnemyBehaviorSystem())
        addSystem(ShieldSystem())
    }
    private val factory = EntityFactory(world)
    private val bossPhases = mutableListOf<Int>()
    private val bossSheet = LagoonSheet(NAGA_FRAME * 11, NAGA_FRAME * 3)

    private fun only(species: EnemySpecies) = StageProgression(
        design = StageDesign(listOf(WaveDesign(listOf(species))), BossKind.NAGA),
        difficulty = 2.05f,
    )

    private fun generator(progression: StageProgression) = EnemyGenerator(
        xSpawnPosition = WIDTH,
        worldHeight = HEIGHT,
        factory = factory,
        enemyPixmap = LagoonSheet(640, 87),
        progression = progression,
        random = Random(20261005),
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

    private fun bat(x: Float = 220f, y: Float = 140f): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, 45f, 40f)))
        world.addComponent(id, PlayerControlComponent())
        world.addComponent(id, HealthComponent(100))
        return id
    }

    // region the lagoon's hostiles

    @Test
    fun `sharks come in from behind along the waterline with only their fins showing`() {
        val generator = generator(only(EnemySpecies.SHARK))
        generator.firstArrival()
        val shark = enemies().single()

        assertEquals(HEIGHT - BURROW_SHOWING, rectOf(shark).top, 0.5f)
        assertTrue(rectOf(shark).right <= 0f, "it arrived in sight, at ${rectOf(shark).left}")
        assertTrue(
            world.getComponent(shark, VelocityComponent::class)!!.velocity.x > 0f,
            "it is not coming from behind"
        )

        // Still cruising half a second later, at the same height and further in: the fin is the warning.
        val left = rectOf(shark).left
        generator.run(0.5f)
        assertEquals(HEIGHT - BURROW_SHOWING, rectOf(shark).top, 1f)
        assertTrue(rectOf(shark).left > left, "it is not overtaking")
    }

    @Test
    fun `a shark leaps forward out of the water up to the bat`() {
        bat(x = 220f, y = 120f)
        val generator = generator(only(EnemySpecies.SHARK))
        generator.firstArrival()
        val shark = enemies().first()
        var highest = Float.MAX_VALUE
        var forwardAtTop = 0f
        repeat((6f / TICK_INITIAL).toInt()) {
            generator.tick()
            if (world.getComponent(shark, TransformComponent::class) == null) return@repeat
            val top = rectOf(shark).top
            if (top < highest) {
                highest = top
                forwardAtTop = world.getComponent(shark, VelocityComponent::class)!!.velocity.x
            }
        }

        assertTrue(highest < 170f, "it never leapt up to the bat: its top got to $highest")
        assertTrue(forwardAtTop > 0f, "it leapt backwards")
    }

    /** Turned to point along its arc from art that faces the way it flies; the rest face left, the bat's way. */
    @Test
    fun `the shark turns from art facing right and the krait from art facing left`() {
        for ((species, artwork) in listOf(
            EnemySpecies.SHARK to 0f,
            EnemySpecies.KRAIT to 180f,
            EnemySpecies.PIRANHA to null
        )) {
            val generator = generator(only(species))
            generator.firstArrival()
            val facing = world.getComponent(enemies().last(), FacesVelocityComponent::class)
            if (artwork == null) assertNull(facing, "$species turns") else assertEquals(
                artwork,
                assertNotNull(facing).artworkDegrees
            )
            enemies().forEach { world.removeEntity(it) }
            world.update(TICK_INITIAL, null)
        }
    }

    @Test
    fun `a puffer throws its spines in a ring`() {
        val generator = generator(only(EnemySpecies.PUFFER))
        generator.firstArrival()
        val gun = assertNotNull(
            world.getComponent(enemies().single(), GunComponent::class),
            "it is unarmed"
        )
        assertTrue(
            gun.volleys.any { it.pattern == ShotPattern.RADIAL && it.count >= 8 },
            "it throws no ring"
        )
    }

    @Test
    fun `a crab always comes in its shell`() {
        val generator = generator(only(EnemySpecies.CRAB))
        generator.firstArrival()
        val shield = assertNotNull(world.getComponent(enemies().single(), ShieldComponent::class))
        assertTrue(shield.isUp)
        assertTrue(shield.regenPerSecond > 0f, "its shell does not grow back")
    }

    // endregion

    // region the Naga

    private fun nagaFight(): Pair<EnemyGenerator, EntityId> {
        val generator = generator(StageProgression.forStage(4))
        generator.run(5 * WAVE_DURATION_SECONDS + 0.1f)
        enemies().filter { it != generator.bossId }.forEach { world.removeEntity(it) }
        world.update(TICK_INITIAL, null)
        return generator to generator.bossId!!
    }

    private fun parts(): List<EntityId> =
        world.query(BossPartComponent::class)
            .filter { !world.hasComponent(it, HealthComponent::class) }

    private fun EnemyGenerator.brain() = bossBrain as NagaBrain

    private fun EnemyGenerator.until(act: NagaBrain.Act, seconds: Float = 30f) {
        var left = (seconds / TICK_INITIAL).toInt()
        while (brain().act != act) {
            check(left-- > 0) { "it never got to $act" }
            tick()
        }
    }

    private fun splashes(): List<EntityId> = world.query(SpriteComponent::class).filter {
        world.getComponent(it, SpriteComponent::class)!!.baseSrcX == NAGA_SPLASH_FRAME * NAGA_FRAME
    }

    @Test
    fun `the lagoon ends on the Naga with a head and twelve parts all under the water`() {
        val (_, head) = nagaFight()

        assertEquals(12, parts().size)
        assertTrue(
            world.hasComponent(head, BossPartComponent::class),
            "the head is not armored against ramming"
        )
        assertNotNull(
            world.getComponent(head, HealthBarComponent::class)?.pinnedTo,
            "its bar goes where it goes"
        )
        assertNotNull(
            world.getComponent(head, ShieldComponent::class),
            "its hood has no shield to raise"
        )
        (parts() + head).forEach {
            assertTrue(
                rectOf(it).top > HEIGHT,
                "a part of it showed before it rose"
            )
        }
    }

    /** It shows itself whole before anything is aimed at the bat: its first rise is at the far right. */
    @Test
    fun `the water boils where it will rise and it rises there`() {
        bat(x = 120f)
        val (generator, head) = nagaFight()
        val brain = generator.brain()

        while (brain.riseX.isNaN()) generator.tick()
        val riseX = brain.riseX
        generator.run(0.5f)
        assertTrue(splashes().isNotEmpty(), "no water was thrown up before it rose")
        splashes().forEach { assertEquals(riseX, rectOf(it).centerX, 1f) }
        assertTrue(riseX > WIDTH * 0.8f, "its first rise was at $riseX, not well away at the right")

        generator.until(NagaBrain.Act.REARED)
        assertTrue(
            rectOf(head).bottom < HEIGHT * 0.75f,
            "it never reared up: its head is at ${rectOf(head).top}"
        )
    }

    /** Reared, its body runs unbroken from the water to its head, however its head sways. */
    @Test
    fun `reared its body runs from the water up to its head`() {
        bat()
        val (generator, head) = nagaFight()
        generator.until(NagaBrain.Act.REARED)
        generator.run(0.8f)

        val chain = mutableListOf(head)
        val left = parts().toMutableList()
        while (left.isNotEmpty()) {
            val last = rectOf(chain.last())
            val next = left.minBy {
                hypot(
                    rectOf(it).centerX - last.centerX,
                    rectOf(it).centerY - last.centerY
                )
            }
            chain += next
            left -= next
        }
        fun gap(front: EntityId, back: EntityId) =
            hypot(
                rectOf(front).centerX - rectOf(back).centerX,
                rectOf(front).centerY - rectOf(back).centerY
            )
        // The neck leaves the hood well below the head's middle, and the head covers the space between.
        assertTrue(
            gap(chain[0], chain[1]) <= NAGA_FRAME / 2f,
            "its neck came away from its head: ${gap(chain[0], chain[1])}"
        )
        chain.drop(1).zipWithNext { front, back ->
            assertTrue(
                gap(front, back) <= NAGA_SPACING_MOST + 1f,
                "two parts came apart: ${gap(front, back)}"
            )
        }
        assertTrue(
            rectOf(chain.last()).top > HEIGHT - NAGA_FRAME / 2f,
            "its tail is out of the water"
        )
    }

    @Test
    fun `from its second phase it coils before it strikes and throws a ring after`() {
        bat(x = 120f, y = 160f)
        val (generator, head) = nagaFight()
        val health = world.getComponent(head, HealthComponent::class)!!
        health.hitPoints = (health.maxHitPoints * 0.75f).toInt()
        generator.tick()
        assertEquals(listOf(2), bossPhases)

        generator.until(NagaBrain.Act.COIL)
        generator.until(NagaBrain.Act.STRIKE, seconds = 2f)
        val from = rectOf(head)
        fired.clear()
        generator.until(NagaBrain.Act.RECOIL, seconds = 2f)
        val to = rectOf(head)
        assertTrue(
            to.centerX < from.centerX - 40f,
            "it did not lunge at the bat: ${from.centerX} to ${to.centerX}"
        )
        generator.run(0.3f)
        assertTrue(head in fired, "it threw nothing from its strike")
    }

    @Test
    fun `from its third phase it calls up its kraits as it rises`() {
        bat()
        val (generator, head) = nagaFight()
        val health = world.getComponent(head, HealthComponent::class)!!
        health.hitPoints = (health.maxHitPoints * 0.5f).toInt()
        generator.tick()

        generator.until(NagaBrain.Act.SUBMERGED)
        generator.until(NagaBrain.Act.RISING)
        val kraits = enemies().filter {
            world.getComponent(
                it,
                EnemyBehaviorComponent::class
            )?.type == at.smiech.engine.ecs.EnemyMovementType.SINE
        }
        assertEquals(2, kraits.size, "a rise in phase three called up ${kraits.size} kraits")
    }

    @Test
    fun `its fourth phase raises its hood's shield and sends it swimming across`() {
        bat()
        val (generator, head) = nagaFight()
        val health = world.getComponent(head, HealthComponent::class)!!
        health.hitPoints = (health.maxHitPoints * 0.35f).toInt()
        generator.tick()

        assertEquals(listOf(2, 3, 4), bossPhases)
        assertTrue(
            world.getComponent(head, ShieldComponent::class)!!.isUp,
            "its hood raised no shield"
        )
        generator.until(NagaBrain.Act.SWIM, seconds = 40f)
        assertTrue(generator.brain().swimming)
        // In from the right, low over the water.
        assertTrue(rectOf(head).left > WIDTH - NAGA_FRAME, "the swim began in sight")
        generator.run(4f)
        assertTrue(rectOf(head).centerY > HEIGHT * 0.6f, "it swam high over the water")
    }

    @Test
    fun `one blow through every threshold plays every phase in order`() {
        val (generator, head) = nagaFight()
        val health = world.getComponent(head, HealthComponent::class)!!

        health.hitPoints = (health.maxHitPoints * 0.1f).toInt()
        generator.tick()

        assertEquals(listOf(2, 3, 4, 5), bossPhases)
    }

    // endregion
}
