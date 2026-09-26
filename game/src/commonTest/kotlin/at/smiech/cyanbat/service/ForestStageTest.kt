package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.BOSS_WAVE
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val MINUTE = WAVE_DURATION_SECONDS

private val FOREST_SPECIES = setOf(
    EnemySpecies.WASP, EnemySpecies.BEETLE, EnemySpecies.SPITTER, EnemySpecies.OWL, EnemySpecies.WISP,
)

/** Stage 2 as designed: what it sends, how hard, and in what order. */
class ForestStageTest {

    private val cave = StageProgression.forStage(1)
    private val forest = StageProgression.forStage(2)

    private fun waves(stage: StageProgression) = (0 until BOSS_WAVE).map { stage.waveAt(it * MINUTE) }

    @Test
    fun `stage 2 is the forest, five waves and the Moth Queen`() {
        assertEquals(StageDesign.FOREST, forest.design)
        assertEquals(5, forest.design.waves.size)
        assertEquals(5, forest.bossWave)
        assertEquals(BossKind.MOTH_QUEEN, forest.design.boss)
    }

    @Test
    fun `the forest sends only its own enemies, and all five of them`() {
        val sent = waves(forest).flatMap { it.enemyTypes }.toSet()

        assertEquals(FOREST_SPECIES, sent)
    }

    @Test
    fun `no two forest waves in a row send the same mix`() {
        waves(forest).map { it.enemyTypes }.zipWithNext { earlier, later ->
            assertNotEquals(earlier, later)
        }
    }

    /** "More difficult" on every axis a wave has, wave for wave. */
    @Test
    fun `every forest wave is tougher than the same wave in the cave`() {
        waves(cave).zip(waves(forest)).forEach { (cave, forest) ->
            assertTrue(forest.hitPoints > cave.hitPoints, "wave ${forest.index}: health")
            assertTrue(forest.damage > cave.damage, "wave ${forest.index}: damage")
            assertTrue(forest.spawnIntervalSeconds < cave.spawnIntervalSeconds, "wave ${forest.index}: pace")
        }
        assertTrue(forest.bossWave().hitPoints > cave.bossWave().hitPoints)
    }

    /** Shields and guns are taught before they are everywhere. */
    @Test
    fun `shields and guns ramp in over the stage and never back off`() {
        val forestWaves = waves(forest)

        forestWaves.zipWithNext { earlier, later ->
            assertTrue(later.shieldChance >= earlier.shieldChance, "shields backed off into wave ${later.index}")
            assertTrue(later.gunChance >= earlier.gunChance, "guns backed off into wave ${later.index}")
        }
        assertEquals(0f, forestWaves.first().shieldChance)
        assertEquals(0f, forestWaves.first().gunChance)
        assertTrue(forestWaves.last().shieldChance > 0f)
        assertTrue(forestWaves.last().gunChance > 0f)
    }

    @Test
    fun `the cave hands out no shields and no guns`() {
        waves(cave).forEach {
            assertEquals(0f, it.shieldChance)
            assertEquals(0f, it.gunChance)
        }
    }

    @Test
    fun `the forest has something that shoots, something shielded, and groups`() {
        assertTrue(FOREST_SPECIES.any { it.gun != null }, "nothing shoots")
        assertTrue(FOREST_SPECIES.any { it.innateShield > 0f }, "nothing is shielded")
        assertTrue(FOREST_SPECIES.any { it.squad == Squad.SWARM }, "nothing swarms")
        assertTrue(FOREST_SPECIES.any { it.squad == Squad.V_FORMATION }, "nothing flies in formation")
    }
}
