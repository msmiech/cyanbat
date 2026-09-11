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

class LevelProgressionTest {

    private val level1 = LevelProgression.forLevel(1)

    // region the shape of the curve

    @Test
    fun `the opening wave is the gentlest thing in the level`() {
        val opening = level1.waveAt(0f)

        assertEquals(0, opening.index)
        assertEquals(ENEMY_BASE_HIT_POINTS, opening.hitPoints)
        assertEquals(ENEMY_BASE_DAMAGE, opening.damage)
        assertEquals(1f, opening.speedMultiplier)
        assertEquals(1, opening.burstSize)
    }

    /** The point of the feature: a run should get harder on every axis the longer it goes on. */
    @Test
    fun `health damage and speed all climb wave over wave`() {
        val waves = (0 until BOSS_WAVE).map { level1.waveAt(it * MINUTE) }

        waves.zipWithNext { earlier, later ->
            assertTrue(later.hitPoints > earlier.hitPoints, "health did not climb into wave ${later.index}")
            assertTrue(later.damage > earlier.damage, "damage did not climb into wave ${later.index}")
            assertTrue(later.speedMultiplier > earlier.speedMultiplier, "speed did not climb into wave ${later.index}")
        }
    }

    @Test
    fun `enemies arrive closer together as the level goes on`() {
        val intervals = (0..BOSS_WAVE).map { level1.spawnIntervalAt(it * MINUTE) }

        intervals.zipWithNext { earlier, later ->
            assertTrue(later < earlier, "the gap did not tighten: $earlier then $later")
        }
    }

    @Test
    fun `the spawn interval runs from the opening gap down to the floor`() {
        assertEquals(OPENING_SPAWN_INTERVAL_SECONDS, level1.spawnIntervalAt(0f))
        assertEquals(MINIMUM_SPAWN_INTERVAL_SECONDS, level1.spawnIntervalAt(level1.bossTimeSeconds), 0.001f)
    }

    /**
     * Regression in spirit: the generator waits out whatever gap it is handed, so a gap that could
     * reach zero would spawn an enemy on every single tick. Drive the clock far past the boss and
     * assert it never gets close.
     */
    @Test
    fun `the spawn interval is never zero or negative however long the level runs`() {
        val random = Random(20260911)
        for (second in 0..3_600) {
            val interval = level1.spawnIntervalAt(second.toFloat())
            assertTrue(interval > 0f, "interval went to $interval at ${second}s")
            assertTrue(level1.nextSpawnDelay(second.toFloat(), random) > 0f, "delay went non-positive at ${second}s")
        }
    }

    @Test
    fun `jitter keeps spawns off a metronome without running away from the interval`() {
        val random = Random(7)
        val interval = level1.spawnIntervalAt(30f)
        val delays = List(50) { level1.nextSpawnDelay(30f, random) }

        assertTrue(delays.toSet().size > 1, "every delay came out identical")
        delays.forEach {
            assertTrue(it in interval * 0.6f..interval * 1.4f, "delay $it strayed far from the $interval interval")
        }
    }

    // endregion

    // region wave boundaries

    @Test
    fun `a new wave begins on every full minute`() {
        assertEquals(0, level1.waveIndexAt(0f))
        assertEquals(0, level1.waveIndexAt(MINUTE - 0.1f))
        assertEquals(1, level1.waveIndexAt(MINUTE))
        assertEquals(3, level1.waveIndexAt(3 * MINUTE + 30f))
    }

    @Test
    fun `the wave index never runs past the boss`() {
        assertEquals(BOSS_WAVE, level1.waveIndexAt(level1.bossTimeSeconds))
        assertEquals(BOSS_WAVE, level1.waveIndexAt(60 * MINUTE))
    }

    /** A wave that spawned the same enemies as the last one would not read as a new group. */
    @Test
    fun `consecutive waves draw from different enemy mixes`() {
        val mixes = (0 until BOSS_WAVE).map { level1.waveAt(it * MINUTE).enemyTypes }

        mixes.zipWithNext { earlier, later ->
            assertNotEquals(earlier, later, "two waves in a row spawned the same mix: $earlier")
        }
    }

    @Test
    fun `every wave draws only from the three enemies on the sheet`() {
        (0..BOSS_WAVE).forEach { index ->
            val types = level1.waveAt(index * MINUTE).enemyTypes
            assertTrue(types.isNotEmpty(), "wave $index has nothing to spawn")
            assertTrue(types.all { it in 0..2 }, "wave $index asks for a sprite that is not on the sheet: $types")
        }
    }

    // endregion

    // region the boss

    @Test
    fun `level 1's boss is due at five minutes and not before`() {
        assertEquals(5 * MINUTE, level1.bossTimeSeconds)
        assertTrue(!level1.isBossDue(5 * MINUTE - 0.1f))
        assertTrue(level1.isBossDue(5 * MINUTE))
    }

    @Test
    fun `the boss outclasses the wave that escorts it in`() {
        val lastWave = level1.waveAt(level1.bossTimeSeconds - 1f)
        val boss = level1.bossWave()

        assertTrue(boss.hitPoints > lastWave.hitPoints * 3, "the boss is not worth a fight")
        assertTrue(boss.damage > lastWave.damage)
    }

    /** A boss health pool is only meaningful as a fight length, and the bat fires once a second. */
    @Test
    fun `the boss fight lasts long enough to be one`() {
        assertTrue(level1.bossShotsToKill >= 20, "the boss dies in ${level1.bossShotsToKill} shots")
    }

    // endregion

    // region levels against each other

    @Test
    fun `a later level opens harder than the one before it`() {
        val level2 = LevelProgression.forLevel(2)

        assertTrue(level2.waveAt(0f).hitPoints > level1.waveAt(0f).hitPoints)
        assertTrue(level2.waveAt(0f).damage > level1.waveAt(0f).damage)
        assertTrue(level2.spawnIntervalAt(0f) < level1.spawnIntervalAt(0f))
    }

    @Test
    fun `level 1 is the baseline, and an unknown level id does not go easier than it`() {
        assertEquals(1f, level1.difficulty)
        assertEquals(1f, LevelProgression.forLevel(0).difficulty)
    }

    // endregion
}
