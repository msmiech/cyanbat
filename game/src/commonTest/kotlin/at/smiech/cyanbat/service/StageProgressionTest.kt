package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.BOSS_WAVE
import at.smiech.cyanbat.util.ENEMY_BASE_DAMAGE
import at.smiech.cyanbat.util.ENEMY_BASE_HIT_POINTS
import at.smiech.cyanbat.util.MINIMUM_SPAWN_INTERVAL_SECONDS
import at.smiech.cyanbat.util.OPENING_SPAWN_INTERVAL_SECONDS
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val MINUTE = WAVE_DURATION_SECONDS

private val CAVE_SPECIES = setOf(EnemySpecies.SCOUT, EnemySpecies.WEAVER, EnemySpecies.STRIKER)

class StageProgressionTest {

    private val stage1 = StageProgression.forStage(1)

    // region the shape of the curve

    @Test
    fun `the opening wave is the gentlest thing in the stage`() {
        val opening = stage1.waveAt(0f)

        assertEquals(0, opening.index)
        assertEquals(ENEMY_BASE_HIT_POINTS, opening.hitPoints)
        assertEquals(ENEMY_BASE_DAMAGE, opening.damage)
        assertEquals(1f, opening.speedMultiplier)
        assertEquals(1, opening.burstSize)
    }

    /** The point of the feature: a run should get harder on every axis the longer it goes on. */
    @Test
    fun `health damage and speed all climb wave over wave`() {
        val waves = (0 until BOSS_WAVE).map { stage1.waveAt(it * MINUTE) }

        waves.zipWithNext { earlier, later ->
            assertTrue(
                later.hitPoints > earlier.hitPoints,
                "health did not climb into wave ${later.index}"
            )
            assertTrue(
                later.damage > earlier.damage,
                "damage did not climb into wave ${later.index}"
            )
            assertTrue(
                later.speedMultiplier > earlier.speedMultiplier,
                "speed did not climb into wave ${later.index}"
            )
        }
    }

    @Test
    fun `enemies arrive closer together as the stage goes on`() {
        val intervals = (0..BOSS_WAVE).map { stage1.spawnIntervalAt(it * MINUTE) }

        intervals.zipWithNext { earlier, later ->
            assertTrue(later < earlier, "the gap did not tighten: $earlier then $later")
        }
    }

    @Test
    fun `the spawn interval runs from the opening gap down to the floor`() {
        assertEquals(OPENING_SPAWN_INTERVAL_SECONDS, stage1.spawnIntervalAt(0f))
        assertEquals(
            MINIMUM_SPAWN_INTERVAL_SECONDS,
            stage1.spawnIntervalAt(stage1.bossTimeSeconds),
            0.001f
        )
    }

    /**
     * Regression in spirit: the generator waits out whatever gap it is handed, so a gap that could
     * reach zero would spawn an enemy on every single tick. Drive the clock far past the boss and
     * assert it never gets close.
     */
    @Test
    fun `the spawn interval is never zero or negative however long the stage runs`() {
        val random = Random(20260911)
        for (second in 0..3_600) {
            val interval = stage1.spawnIntervalAt(second.toFloat())
            assertTrue(interval > 0f, "interval went to $interval at ${second}s")
            assertTrue(
                stage1.nextSpawnDelay(second.toFloat(), random) > 0f,
                "delay went non-positive at ${second}s"
            )
        }
    }

    @Test
    fun `jitter keeps spawns off a metronome without running away from the interval`() {
        val random = Random(7)
        val interval = stage1.spawnIntervalAt(30f)
        val delays = List(50) { stage1.nextSpawnDelay(30f, random) }

        assertTrue(delays.toSet().size > 1, "every delay came out identical")
        delays.forEach {
            assertTrue(
                it in interval * 0.6f..interval * 1.4f,
                "delay $it strayed far from the $interval interval"
            )
        }
    }

    // endregion

    // region wave boundaries

    @Test
    fun `a new wave begins on every full minute`() {
        assertEquals(0, stage1.waveIndexAt(0f))
        assertEquals(0, stage1.waveIndexAt(MINUTE - 0.1f))
        assertEquals(1, stage1.waveIndexAt(MINUTE))
        assertEquals(3, stage1.waveIndexAt(3 * MINUTE + 30f))
    }

    @Test
    fun `the wave index never runs past the boss`() {
        assertEquals(BOSS_WAVE, stage1.waveIndexAt(stage1.bossTimeSeconds))
        assertEquals(BOSS_WAVE, stage1.waveIndexAt(60 * MINUTE))
    }

    /** A wave that spawned the same enemies as the last one would not read as a new group. */
    @Test
    fun `consecutive waves draw from different enemy mixes`() {
        val mixes = (0 until BOSS_WAVE).map { stage1.waveAt(it * MINUTE).enemyTypes }

        mixes.zipWithNext { earlier, later ->
            assertNotEquals(earlier, later, "two waves in a row spawned the same mix: $earlier")
        }
    }

    @Test
    fun `every wave of the cave draws only from the cave's three drones`() {
        (0..BOSS_WAVE).forEach { index ->
            val types = stage1.waveAt(index * MINUTE).enemyTypes
            assertTrue(types.isNotEmpty(), "wave $index has nothing to spawn")
            assertTrue(
                types.all { it in CAVE_SPECIES },
                "wave $index asks for a sprite that is not on the cave's sheet: $types"
            )
        }
    }

    // endregion

    // region the boss

    @Test
    fun `stage 1's boss is due at five minutes and not before`() {
        assertEquals(5 * MINUTE, stage1.bossTimeSeconds)
        assertTrue(!stage1.isBossDue(5 * MINUTE - 0.1f))
        assertTrue(stage1.isBossDue(5 * MINUTE))
    }

    @Test
    fun `the boss outclasses the wave that escorts it in`() {
        val lastWave = stage1.waveAt(stage1.bossTimeSeconds - 1f)
        val boss = stage1.bossWave()

        assertTrue(boss.hitPoints > lastWave.hitPoints * 3, "the boss is not worth a fight")
        assertTrue(boss.damage > lastWave.damage)
    }

    /** A boss health pool is only meaningful as a fight length, and the bat fires once a second. */
    @Test
    fun `the boss fight lasts long enough to be one`() {
        assertTrue(stage1.bossShotsToKill >= 20, "the boss dies in ${stage1.bossShotsToKill} shots")
    }

    // endregion

    // region stages against each other

    @Test
    fun `a later stage opens harder than the one before it`() {
        val stage2 = StageProgression.forStage(2)

        assertTrue(stage2.waveAt(0f).hitPoints > stage1.waveAt(0f).hitPoints)
        assertTrue(stage2.waveAt(0f).damage > stage1.waveAt(0f).damage)
        assertTrue(stage2.spawnIntervalAt(0f) < stage1.spawnIntervalAt(0f))
    }

    @Test
    fun `stage 1 is the baseline, and an unknown stage id does not go easier than it`() {
        assertEquals(1f, stage1.difficulty)
        assertEquals(1f, StageProgression.forStage(0).difficulty)
    }

    // endregion
}
