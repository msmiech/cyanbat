package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.EliteComponent
import at.smiech.cyanbat.ecs.ElitePalette
import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.util.ELITE_FIRE_INTERVAL_FACTOR
import at.smiech.cyanbat.util.ELITE_HIT_POINT_FACTOR
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.AuraComponent
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.ProjectileStyleComponent
import at.smiech.engine.ecs.ShieldComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.ecs.World
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class EliteSheet(override val width: Int, override val height: Int) : Pixmap {
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/** Which enemies the generator sends as elites, and what it makes of them. */
class EliteGeneratorTest {

    private val world = World()
    private val factory = EntityFactory(world)
    private val bossPhases = mutableListOf<Int>()

    /** A stage that sends nothing but [species], every group of it with an elite in it. */
    private fun only(species: EnemySpecies, eliteChance: Float = 1f, boss: BossKind = BossKind.CACO_IMP) =
        StageProgression(
            design = StageDesign(
                waves = listOf(WaveDesign(listOf(species), eliteChance = eliteChance)),
                boss = boss,
                bossName = "TEST",
            ),
        )

    private fun generator(progression: StageProgression) = EnemyGenerator(
        xSpawnPosition = FRAME_BUFFER_WIDTH,
        worldHeight = FRAME_BUFFER_HEIGHT,
        factory = factory,
        enemyPixmap = EliteSheet(640, 87),
        progression = progression,
        random = Random(20261001),
        bossPixmap = EliteSheet(384, 240),
        onBossPhaseChanged = { bossPhases += it },
    )

    private fun EnemyGenerator.run(seconds: Float) {
        repeat((seconds / TICK_INITIAL).toInt()) { update(TICK_INITIAL) }
    }

    private fun EnemyGenerator.firstArrival() {
        while (enemies().isEmpty()) update(TICK_INITIAL)
    }

    private fun enemies(): List<EntityId> = world.query(EnemyBehaviorComponent::class)
    private fun elites(): List<EntityId> = enemies().filter { world.hasComponent(it, EliteComponent::class) }
    private fun paletteOf(id: EntityId) = world.getComponent(id, EliteComponent::class)!!.palette

    // region who is one

    @Test
    fun `a wave that sends no elites sends none`() {
        generator(only(EnemySpecies.SCOUT, eliteChance = 0f)).run(20f)

        assertTrue(enemies().isNotEmpty())
        assertTrue(elites().isEmpty(), "${elites().size} elites in a wave that sends none")
    }

    @Test
    fun `a loner that comes up elite is one`() {
        generator(only(EnemySpecies.SCOUT)).run(20f)

        assertTrue(enemies().isNotEmpty())
        assertEquals(enemies(), elites())
    }

    /** One elite stands out from its swarm; a swarm of them would just be a harder swarm. */
    @Test
    fun `a swarm brings one elite and no more`() {
        generator(only(EnemySpecies.WASP)).firstArrival()

        assertTrue(enemies().size > 1, "not a swarm")
        assertEquals(1, elites().size)
    }

    @Test
    fun `a formation's elite is its leader`() {
        generator(only(EnemySpecies.WISP)).firstArrival()

        val leader = enemies().minBy { world.getComponent(it, TransformComponent::class)!!.rect.left }
        assertEquals(listOf(leader), elites())
    }

    @Test
    fun `elites come in every palette`() {
        generator(only(EnemySpecies.SCOUT)).run(2 * WAVE_DURATION_SECONDS)

        assertEquals(ElitePalette.entries.toSet(), elites().map { paletteOf(it) }.toSet())
    }

    /** The opening minute is for learning a stage's own enemies; the rest of it sends elites. */
    @Test
    fun `every stage sends elites from its second minute and not before`() {
        for (stage in 1..3) {
            val waves = StageDesign.forStage(stage).waves
            assertEquals(0f, waves.first().eliteChance, "stage $stage's opening minute")
            for ((index, wave) in waves.withIndex().drop(1)) {
                assertTrue(wave.eliteChance > 0f, "stage $stage's wave ${index + 1} sends no elites")
            }
        }
    }

    // endregion

    // region what one is

    @Test
    fun `an elite is tougher than an ordinary one of its kind`() {
        val progression = only(EnemySpecies.SCOUT)
        generator(progression).firstArrival()

        val ordinary = progression.waveAt(0f).hitPoints * EnemySpecies.SCOUT.hitPointFactor
        val health = world.getComponent(elites().single(), HealthComponent::class)!!
        assertEquals((ordinary.roundToInt() * ELITE_HIT_POINT_FACTOR).roundToInt(), health.hitPoints)
        assertEquals(health.hitPoints, health.maxHitPoints)
    }

    /** It is what is inside the shell that is tougher; two and a half shells would be a wall. */
    @Test
    fun `an elite's shell is no thicker than its kind's`() {
        val progression = only(EnemySpecies.BEETLE)
        generator(progression).firstArrival()

        val ordinary = (progression.waveAt(0f).hitPoints * EnemySpecies.BEETLE.hitPointFactor).roundToInt()
        val shield = assertNotNull(world.getComponent(elites().single(), ShieldComponent::class))
        assertEquals((ordinary * EnemySpecies.BEETLE.innateShield).roundToInt(), shield.maxPoints)
    }

    /** The cave's imps carry no guns, but their elites do: the colored fire is half of telling one. */
    @Test
    fun `an elite of a kind that never shoots is issued a gun and fires it faster`() {
        generator(only(EnemySpecies.SCOUT)).firstArrival()

        val elite = elites().single()
        val weapon = assertNotNull(world.getComponent(elite, WeaponComponent::class))
        assertEquals(EnemySpecies.ISSUED_GUN.interval * ELITE_FIRE_INTERVAL_FACTOR, weapon.interval, 1e-5f)
        assertEquals(EnemySpecies.ISSUED_GUN.volleys, world.getComponent(elite, GunComponent::class)?.volleys)
    }

    @Test
    fun `an elite of a kind that shoots fires its own gun faster`() {
        generator(only(EnemySpecies.SPITTER)).firstArrival()

        val elite = elites().single()
        val gun = EnemySpecies.SPITTER.gun!!
        assertEquals(gun.interval * ELITE_FIRE_INTERVAL_FACTOR, world.getComponent(elite, WeaponComponent::class)!!.interval, 1e-5f)
        assertEquals(gun.volleys, world.getComponent(elite, GunComponent::class)?.volleys)
    }

    @Test
    fun `an elite glows in its palette and fires in the same colors`() {
        generator(only(EnemySpecies.SCOUT)).run(20f)

        for (elite in elites()) {
            val palette = paletteOf(elite)
            val aura = assertNotNull(world.getComponent(elite, AuraComponent::class), "$palette has no glow")
            assertEquals(palette.aura, aura.colors)
            assertTrue(aura.intensity > 0f && aura.tier > 0, "$palette's glow is out")
            assertEquals(palette.shotVariant, world.getComponent(elite, ProjectileStyleComponent::class)?.variant)
        }
    }

    @Test
    fun `an ordinary enemy has no glow`() {
        generator(only(EnemySpecies.SCOUT, eliteChance = 0f)).run(20f)

        assertTrue(enemies().isNotEmpty())
        enemies().forEach { assertNull(world.getComponent(it, AuraComponent::class)) }
    }

    // endregion

    // region the boss

    /**
     * The fight is the boss's. Its summons are sent at the escort's strength, and a glow among them
     * would draw the player's fire off the boss - so even a stage whose every group brings an elite
     * gets none in what the boss calls up.
     */
    @Test
    fun `nothing a boss calls up is an elite`() {
        val generator = generator(only(EnemySpecies.WASP, boss = BossKind.MOTH_QUEEN))
        generator.run(5 * WAVE_DURATION_SECONDS + 1f)
        val queen = assertNotNull(generator.bossId)
        enemies().filter { it != queen }.forEach { world.removeEntity(it) }
        world.update(TICK_INITIAL, null)

        val health = world.getComponent(queen, HealthComponent::class)!!
        health.hitPoints = (health.maxHitPoints * 0.6f).toInt()
        generator.run(4f)

        assertEquals(listOf(2), bossPhases)
        assertTrue(enemies().size > 1, "no swarm was called in")
        assertTrue(elites().isEmpty(), "the boss called up ${elites().size} elites")
    }

    // endregion
}
