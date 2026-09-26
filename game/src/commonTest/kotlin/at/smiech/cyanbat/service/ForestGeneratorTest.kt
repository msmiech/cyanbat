package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.util.SWARM_SIZE
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyBehaviorSystem
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.LifetimeSystem
import at.smiech.engine.ecs.MovementSystem
import at.smiech.engine.ecs.ShieldComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.ecs.World
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class SheetStub(override val width: Int, override val height: Int) : Pixmap {
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/** How the generator turns the forest's designs into swarms, formations, shields and guns. */
class ForestGeneratorTest {

    private val world = World()
    private val factory = EntityFactory(world)
    private val bossPhases = mutableListOf<Int>()

    /** A stage that sends nothing but [species], for looking at one of them at a time. */
    private fun only(species: EnemySpecies, shieldChance: Float = 0f, gunChance: Float = 0f) =
        StageProgression(
            design = StageDesign(
                waves = listOf(WaveDesign(listOf(species), shieldChance, gunChance)),
                boss = BossKind.MOTH_QUEEN,
                bossName = "TEST",
            ),
            difficulty = 1.35f,
        )

    private fun generator(progression: StageProgression) = EnemyGenerator(
        xSpawnPosition = 480,
        worldHeight = 320,
        factory = factory,
        enemyPixmap = SheetStub(640, 29),
        progression = progression,
        random = Random(20260925),
        bossPixmap = SheetStub(384, 80),
        onBossPhaseChanged = { bossPhases += it },
    )

    private fun EnemyGenerator.run(seconds: Float) {
        repeat((seconds / TICK_INITIAL).toInt()) { update(TICK_INITIAL) }
    }

    /** Runs until the first group has arrived, and no further. */
    private fun EnemyGenerator.firstArrival() {
        while (enemies().isEmpty()) update(TICK_INITIAL)
    }

    private fun enemies(): List<EntityId> = world.query(EnemyBehaviorComponent::class)
    private fun behaviorOf(id: EntityId) = world.getComponent(id, EnemyBehaviorComponent::class)!!
    private fun rectOf(id: EntityId) = world.getComponent(id, TransformComponent::class)!!.rect

    // region groups

    @Test
    fun `wasps arrive as a swarm sharing one path`() {
        generator(only(EnemySpecies.WASP)).firstArrival()

        val swarm = enemies()
        assertTrue(swarm.size >= SWARM_SIZE, "a swarm of ${swarm.size}")
        assertTrue(swarm.all { behaviorOf(it).type == EnemyMovementType.SWARM })
        assertEquals(1, swarm.map { behaviorOf(it).initialY }.toSet().size, "the swarm has more than one path")
        assertTrue(swarm.map { behaviorOf(it).phase }.toSet().size > 1, "the swarm buzzes in unison")
    }

    @Test
    fun `wisps arrive as a V of five, the leader at the point`() {
        generator(only(EnemySpecies.WISP)).firstArrival()

        val formation = enemies()
        assertEquals(5, formation.size)
        val leader = formation.minBy { rectOf(it).left }
        val leaderY = rectOf(leader).top
        val wings = formation - leader
        assertEquals(2, wings.count { rectOf(it).top < leaderY }, "the upper wing")
        assertEquals(2, wings.count { rectOf(it).top > leaderY }, "the lower wing")
    }

    /**
     * Regression: a group's trailing members spawn beyond the right edge, and the lifetime cull
     * deleted them on the tick they arrived - in play, only the leader of every group ever flew.
     * Run through the real systems, because the generator alone cannot see it.
     */
    @Test
    fun `every member of a group survives its first moments on the way in`() {
        world.addSystem(EnemyBehaviorSystem())
        world.addSystem(MovementSystem())
        world.addSystem(LifetimeSystem(480, 320))

        for (species in listOf(EnemySpecies.WASP, EnemySpecies.WISP)) {
            val generator = generator(only(species))
            generator.firstArrival()
            val arrived = enemies().size

            repeat(30) { world.update(TICK_INITIAL, null) }

            assertEquals(arrived, enemies().size, "$species lost members on the way in")
            enemies().forEach { world.removeEntity(it) }
            world.update(TICK_INITIAL, null)
        }
    }

    @Test
    fun `a group buys the player more time before the next arrival than a loner does`() {
        val swarms = generator(only(EnemySpecies.WASP))
        swarms.run(WAVE_DURATION_SECONDS)
        val swarmEvents = enemies().size / SWARM_SIZE.toFloat()

        enemies().forEach { world.removeEntity(it) }
        world.update(TICK_INITIAL, null)

        generator(only(EnemySpecies.OWL)).run(WAVE_DURATION_SECONDS)
        val loners = enemies().size

        assertTrue(swarmEvents < loners, "$swarmEvents swarms against $loners loners in a minute")
    }

    // endregion

    // region shields and guns

    @Test
    fun `beetles always arrive shielded`() {
        generator(only(EnemySpecies.BEETLE)).run(10f)

        assertTrue(enemies().isNotEmpty())
        enemies().forEach { id ->
            val shield = assertNotNull(world.getComponent(id, ShieldComponent::class), "an unshielded beetle")
            val health = world.getComponent(id, HealthComponent::class)!!
            assertTrue(shield.isUp)
            assertTrue(shield.points < health.hitPoints, "a shield tougher than the beetle inside it")
        }
    }

    @Test
    fun `spitters always arrive armed, and aim`() {
        generator(only(EnemySpecies.SPITTER)).run(10f)

        assertTrue(enemies().isNotEmpty())
        enemies().forEach { id ->
            assertNotNull(world.getComponent(id, WeaponComponent::class))
            assertNotNull(world.getComponent(id, GunComponent::class))
        }
    }

    @Test
    fun `a wave's shield and gun chances hand out shields and guns`() {
        generator(only(EnemySpecies.OWL, shieldChance = 1f, gunChance = 1f)).run(10f)

        assertTrue(enemies().isNotEmpty())
        enemies().forEach { id ->
            assertNotNull(world.getComponent(id, ShieldComponent::class))
            assertNotNull(world.getComponent(id, GunComponent::class))
        }
    }

    @Test
    fun `nothing is shielded or armed where the wave does not say so`() {
        generator(only(EnemySpecies.OWL)).run(10f)

        assertTrue(enemies().isNotEmpty())
        enemies().forEach { id ->
            assertNull(world.getComponent(id, ShieldComponent::class))
            assertNull(world.getComponent(id, GunComponent::class))
        }
    }

    /** Wasps come six at a time; a shield on each would turn a swarm into a wall. */
    @Test
    fun `wasps never get shields, whatever the wave says`() {
        generator(only(EnemySpecies.WASP, shieldChance = 1f)).run(10f)

        enemies().forEach { assertNull(world.getComponent(it, ShieldComponent::class)) }
    }

    @Test
    fun `hoverers and divers each pick a station on screen`() {
        generator(only(EnemySpecies.OWL)).run(20f)

        enemies().forEach { id ->
            assertTrue(behaviorOf(id).holdX in 200f..420f, "a station at ${behaviorOf(id).holdX}")
        }
    }

    // endregion

    // region the Moth Queen

    private fun queenFight(): Pair<EnemyGenerator, EntityId> {
        val generator = generator(StageProgression.forStage(2))
        generator.run(5 * WAVE_DURATION_SECONDS + 1f)
        enemies().filter { it != generator.bossId }.forEach { world.removeEntity(it) }
        world.update(TICK_INITIAL, null)
        return generator to generator.bossId!!
    }

    @Test
    fun `the forest ends on the Moth Queen, flying her figure eight behind a shield not yet raised`() {
        val (_, queen) = queenFight()

        assertEquals(EnemyMovementType.BOSS_FIGURE_EIGHT, behaviorOf(queen).type)
        assertNotNull(world.getComponent(queen, GunComponent::class))
        assertTrue(world.getComponent(queen, ShieldComponent::class)?.isUp == false)
    }

    @Test
    fun `wounding her into phase two raises her shield and brings on the swarms`() {
        val (generator, queen) = queenFight()
        val health = world.getComponent(queen, HealthComponent::class)!!

        health.hitPoints = (health.maxHitPoints * 0.6f).toInt()
        generator.update(TICK_INITIAL)

        assertEquals(listOf(2), bossPhases)
        assertTrue(world.getComponent(queen, ShieldComponent::class)!!.isUp)

        generator.run(4f)
        val wasps = enemies().filter { it != queen }
        assertTrue(wasps.size >= SWARM_SIZE, "no swarm was called in")
        assertTrue(wasps.all { behaviorOf(it).type == EnemyMovementType.SWARM })
    }

    @Test
    fun `one blow past both thresholds still plays both phases, in order`() {
        val (generator, queen) = queenFight()
        val health = world.getComponent(queen, HealthComponent::class)!!

        health.hitPoints = (health.maxHitPoints * 0.2f).toInt()
        generator.update(TICK_INITIAL)

        assertEquals(listOf(2, 3), bossPhases)
        assertTrue(behaviorOf(queen).tempo > 1f, "enraged, but no faster")
    }

    // endregion
}
