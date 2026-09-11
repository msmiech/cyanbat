package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.BOSS_WAVE
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.DamageComponent
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.World
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stands in for the enemy sheet: only its height is ever read out of a pixmap here. */
private class FakePixmap(override val width: Int = 201, override val height: Int = 29) : Pixmap {
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

private const val MINUTE = WAVE_DURATION_SECONDS
private const val WORLD_WIDTH = 480
private const val WORLD_HEIGHT = 320

class EnemyGeneratorTest {

    private val world = World()
    private val factory = EntityFactory(world)
    private val wavesAnnounced = mutableListOf<Int>()
    private val bossesSpawned = mutableListOf<EntityId>()

    private fun generator(progression: LevelProgression = LevelProgression.forLevel(1)) = EnemyGenerator(
        xSpawnPosition = WORLD_WIDTH,
        worldHeight = WORLD_HEIGHT,
        factory = factory,
        enemyPixmap = FakePixmap(),
        progression = progression,
        random = Random(20260911),
        onWaveChanged = { wavesAnnounced += it.index },
        onBossSpawned = { bossesSpawned += it },
    )

    /** Runs the generator forward the way the game does: a fixed tick, over and over. */
    private fun EnemyGenerator.run(seconds: Float) {
        repeat((seconds / TICK_INITIAL).toInt()) { update(TICK_INITIAL) }
    }

    private fun enemies(): List<EntityId> = world.query(EnemyBehaviorComponent::class)

    private fun bossCount(): Int = enemies().count {
        world.getComponent(it, EnemyBehaviorComponent::class)?.type == EnemyMovementType.BOSS
    }

    // region the ramp

    @Test
    fun `the opening minute is sparse`() {
        val generator = generator()

        generator.run(MINUTE)

        // At a 2.6s opening gap easing towards 0.9s, a minute is well under 40 arrivals; the
        // point of the assertion is that it is nothing like a tick-by-tick flood.
        assertTrue(enemies().size in 15..40, "the first minute spawned ${enemies().size} enemies")
    }

    @Test
    fun `a later minute spawns more than the first one did`() {
        val generator = generator()

        generator.run(MINUTE)
        val firstMinute = enemies().size

        // Clear the field so the next count is arrivals, not survivors.
        enemies().forEach { world.removeEntity(it) }
        world.update(TICK_INITIAL, null)

        generator.run(MINUTE)
        val fifthMinute = enemies().size

        assertTrue(fifthMinute > firstMinute, "minute two spawned $fifthMinute against minute one's $firstMinute")
    }

    @Test
    fun `enemies arrive weaker in the first minute than in the fourth`() {
        val generator = generator()

        generator.run(5f)
        val early = enemies().first()
        val earlyHealth = world.getComponent(early, HealthComponent::class)!!.hitPoints
        val earlyDamage = world.getComponent(early, DamageComponent::class)!!.amount

        generator.run(3 * MINUTE)
        val late = enemies().last()
        val lateHealth = world.getComponent(late, HealthComponent::class)!!.hitPoints
        val lateDamage = world.getComponent(late, DamageComponent::class)!!.amount

        assertTrue(lateHealth > earlyHealth, "health did not grow: $earlyHealth then $lateHealth")
        assertTrue(lateDamage > earlyDamage, "damage did not grow: $earlyDamage then $lateDamage")
    }

    /** An enemy already on screen keeps the strength it arrived with when the wave turns over. */
    @Test
    fun `a wave turning over does not re-arm the enemies already in flight`() {
        val generator = generator()

        generator.run(5f)
        val early = enemies().first()
        val healthAtSpawn = world.getComponent(early, HealthComponent::class)!!.hitPoints

        generator.run(2 * MINUTE)

        assertEquals(healthAtSpawn, world.getComponent(early, HealthComponent::class)!!.hitPoints)
    }

    // endregion

    // region waves

    @Test
    fun `every full minute announces its wave, once`() {
        val generator = generator()

        generator.run(3 * MINUTE + 5f)

        // The opening wave is not announced - there is nothing to announce a change from.
        assertEquals(listOf(1, 2, 3), wavesAnnounced)
    }

    @Test
    fun `the wave in force tracks the clock`() {
        val generator = generator()

        generator.run(2 * MINUTE + 10f)

        assertEquals(2, generator.currentWave.index)
    }

    // endregion

    // region the boss

    @Test
    fun `the boss arrives at five minutes and not before`() {
        val generator = generator()

        generator.run(5 * MINUTE - 10f)
        assertEquals(0, bossCount(), "a boss turned up early")
        assertNull(generator.bossId)

        generator.run(11f)
        assertEquals(1, bossCount())
        assertNotNull(generator.bossId)
        assertEquals(listOf(generator.bossId), bossesSpawned)
    }

    @Test
    fun `the boss is spawned exactly once however long the fight runs`() {
        val generator = generator()

        generator.run(9 * MINUTE)

        assertEquals(1, bossCount())
        assertEquals(1, bossesSpawned.size)
    }

    @Test
    fun `no more ordinary enemies arrive once the boss is out`() {
        val generator = generator()

        generator.run(5 * MINUTE + 1f)
        enemies().filter { it != generator.bossId }.forEach { world.removeEntity(it) }
        world.update(TICK_INITIAL, null)

        generator.run(MINUTE)

        assertEquals(listOf(generator.bossId), enemies())
    }

    @Test
    fun `the boss carries a health pool and damage worth the wave it replaces`() {
        val generator = generator()
        generator.run(5 * MINUTE + 1f)
        val boss = generator.bossId!!

        val lastWave = LevelProgression.forLevel(1).waveAt(BOSS_WAVE * MINUTE - 1f)
        assertTrue(world.getComponent(boss, HealthComponent::class)!!.hitPoints > lastWave.hitPoints)
        assertTrue(world.getComponent(boss, DamageComponent::class)!!.amount > lastWave.damage)
    }

    // endregion

    // region the clock

    /**
     * The level clock is fed from the game's fixed tick, so a paused game is a paused level. The
     * generator this replaced slept on a coroutine and went on counting while the player was away.
     */
    @Test
    fun `a generator that is not updated does not advance its level`() {
        val generator = generator()

        generator.run(30f)
        val spawnedBeforePause = enemies().size
        val elapsedBeforePause = generator.elapsedSeconds

        // A pause is simply not calling update, however long it lasts.
        assertEquals(spawnedBeforePause, enemies().size)
        assertEquals(elapsedBeforePause, generator.elapsedSeconds)
    }

    @Test
    fun `starting a new level puts the clock, the wave and the boss back to the beginning`() {
        val generator = generator()
        generator.run(5 * MINUTE + 1f)

        generator.startLevel(LevelProgression.forLevel(2))

        assertEquals(0f, generator.elapsedSeconds)
        assertEquals(0, generator.currentWave.index)
        assertTrue(!generator.bossSpawned)
        assertNull(generator.bossId)
    }

    // endregion
}
