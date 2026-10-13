package at.smiech.cyanbat.service

import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.util.ENDLESS_ROUND_WAVES
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** How [EndlessCourse] deals the endless stage's rounds. */
class EndlessCourseTest {

    private fun rounds(count: Int, seed: Int = 7, difficulty: Difficulty = Difficulty.HARD): List<StageProgression> {
        val course = EndlessCourse(Random(seed), difficulty)
        return List(count) { course.next() }
    }

    /** The wave in force [wave] waves into [round], from 0. */
    private fun waveOf(round: StageProgression, wave: Int): EnemyWave =
        round.waveAt(wave * WAVE_DURATION_SECONDS + 1f)

    @Test
    fun `each round numbers its waves on from the last`() {
        val dealt = rounds(5)
        dealt.forEachIndexed { round, progression ->
            assertEquals(round * ENDLESS_ROUND_WAVES, progression.wavesBefore)
            assertEquals(ENDLESS_ROUND_WAVES, progression.bossWave)
            for (wave in 0 until ENDLESS_ROUND_WAVES) {
                assertEquals(round * ENDLESS_ROUND_WAVES + wave, waveOf(progression, wave).index)
            }
        }
    }

    @Test
    fun `every wave is tougher than the one before it across rounds`() {
        val waves = rounds(8).flatMap { round -> List(ENDLESS_ROUND_WAVES) { waveOf(round, it) } }
        waves.zipWithNext().forEach { (before, after) ->
            assertTrue(
                after.hitPoints > before.hitPoints,
                "wave ${after.index + 1} has ${after.hitPoints} health after ${before.hitPoints}",
            )
        }
    }

    @Test
    fun `each round opens harder than the one before`() {
        val dealt = rounds(10)
        dealt.zipWithNext().forEach { (before, after) ->
            assertTrue(after.difficulty > before.difficulty)
            assertTrue(after.bossDifficulty > before.bossDifficulty)
        }
    }

    @Test
    fun `a boss keeps the health its own stage gives its longer fight`() {
        val dealt = rounds(BossKind.entries.size * 2)
        for (round in dealt) {
            val ordinary = round.copy(design = round.design.copy(bossVitality = 1f)).bossWave().hitPoints
            val expected = (ordinary * EndlessCourse.vitalityOf(round.design.boss)).toInt()
            assertTrue(kotlin.math.abs(round.bossWave().hitPoints - expected) <= 2, "${round.design.boss}")
        }
    }

    @Test
    fun `every boss comes before any comes twice and none twice running`() {
        val bosses = rounds(BossKind.entries.size * 6, seed = 11).map { it.design.boss }
        bosses.chunked(BossKind.entries.size).forEach { bag ->
            assertEquals(BossKind.entries.toSet(), bag.toSet(), "a bag of bosses repeated one: $bag")
        }
        bosses.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b, "the same boss came twice running") }
    }

    @Test
    fun `the first wave is two species with no extras`() {
        repeat(20) { seed ->
            val first = rounds(1, seed).single().design.waves.first()
            assertEquals(2, first.species.size)
            assertEquals(0f, first.eliteChance)
            assertEquals(0f, first.gunChance)
        }
    }

    @Test
    fun `waves grow to four species and never pair two of a kind of arrival`() {
        val designs = rounds(6, seed = 3).flatMap { it.design.waves }
        assertEquals(4, designs.last().species.size)
        val low = setOf(Squad.BURROW, Squad.FROM_BEHIND)
        for (design in designs) {
            assertEquals(design.species.size, design.species.toSet().size, "a species twice in $design")
            for (squad in listOf(Squad.SWARM, Squad.V_FORMATION)) {
                assertTrue(design.species.count { it.squad == squad } <= 1, "two of $squad in $design")
            }
            assertTrue(design.species.count { it.squad in low } <= 1, "two from below or behind in $design")
        }
    }

    @Test
    fun `shields guns and elites ramp up and level off`() {
        val designs = rounds(30).flatMap { it.design.waves }
        designs.zipWithNext().forEach { (before, after) ->
            assertTrue(after.shieldChance >= before.shieldChance)
            assertTrue(after.gunChance >= before.gunChance)
            assertTrue(after.eliteChance >= before.eliteChance)
        }
        assertTrue(designs.last().eliteChance <= 0.1f)
        assertTrue(designs.last().shieldChance <= 0.45f)
    }

    @Test
    fun `an easier difficulty thins every round's waves`() {
        val hard = rounds(3, difficulty = Difficulty.HARD)
        val easy = rounds(3, difficulty = Difficulty.EASY)
        hard.zip(easy).forEach { (h, e) ->
            assertTrue(e.spawnIntervalAt(30f) > h.spawnIntervalAt(30f))
            assertEquals(waveOf(h, 1).hitPoints, waveOf(e, 1).hitPoints)
        }
    }

    @Test
    fun `the same seed deals the same rounds`() {
        assertEquals(rounds(6, seed = 42).map { it.design }, rounds(6, seed = 42).map { it.design })
    }
}
