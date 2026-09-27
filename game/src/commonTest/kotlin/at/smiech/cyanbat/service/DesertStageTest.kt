package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.BOSS_WAVE
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import at.smiech.engine.ecs.EnemyMovementType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val MINUTE = WAVE_DURATION_SECONDS

private val DESERT_SPECIES = setOf(
    EnemySpecies.LOCUST, EnemySpecies.HAWK, EnemySpecies.WYRMLING, EnemySpecies.DJINN, EnemySpecies.SCARAB,
)

/** Stage 3 as designed: what it sends, how hard, and in what order. */
class DesertStageTest {

    private val forest = StageProgression.forStage(2)
    private val desert = StageProgression.forStage(3)

    private fun waves(stage: StageProgression) = (0 until BOSS_WAVE).map { stage.waveAt(it * MINUTE) }

    @Test
    fun `stage 3 is the desert with five waves and the Sand Wyrm`() {
        assertEquals(StageDesign.DESERT, desert.design)
        assertEquals(5, desert.design.waves.size)
        assertEquals(5, desert.bossWave)
        assertEquals(BossKind.SAND_WYRM, desert.design.boss)
    }

    /** Past the last stage its design repeats, harder, rather than falling back to an earlier one. */
    @Test
    fun `a stage past the desert is the desert again`() {
        assertEquals(StageDesign.DESERT, StageDesign.forStage(4))
    }

    @Test
    fun `the desert sends only its own enemies and all five of them`() {
        assertEquals(DESERT_SPECIES, waves(desert).flatMap { it.enemyTypes }.toSet())
    }

    @Test
    fun `no two desert waves in a row send the same mix`() {
        waves(desert).map { it.enemyTypes }.zipWithNext { earlier, later -> assertNotEquals(earlier, later) }
    }

    @Test
    fun `every desert wave is tougher than the same wave in the forest`() {
        waves(forest).zip(waves(desert)).forEach { (forest, desert) ->
            assertTrue(desert.hitPoints > forest.hitPoints, "wave ${desert.index}: health")
            assertTrue(desert.damage > forest.damage, "wave ${desert.index}: damage")
            assertTrue(desert.spawnIntervalSeconds < forest.spawnIntervalSeconds, "wave ${desert.index}: pace")
        }
        assertTrue(desert.bossWave().hitPoints > forest.bossWave().hitPoints)
    }

    @Test
    fun `shields and guns ramp in over the stage and never back off`() {
        val desertWaves = waves(desert)

        desertWaves.zipWithNext { earlier, later ->
            assertTrue(later.shieldChance >= earlier.shieldChance, "shields backed off into wave ${later.index}")
            assertTrue(later.gunChance >= earlier.gunChance, "guns backed off into wave ${later.index}")
        }
        assertEquals(0f, desertWaves.first().shieldChance)
        assertEquals(0f, desertWaves.first().gunChance)
    }

    /** Something new to watch is taught before everything is thrown at the player at once. */
    @Test
    fun `nothing comes up out of the sand in the opening minute`() {
        assertTrue(waves(desert).first().enemyTypes.none { it.squad == Squad.BURROW })
        assertTrue(waves(desert)[1].enemyTypes.any { it.squad == Squad.BURROW }, "the leapers never arrive early")
    }

    @Test
    fun `the desert has a swarm and a leaper and a looper and a shooter and a shell that grows back`() {
        assertTrue(DESERT_SPECIES.any { it.squad == Squad.SWARM }, "nothing swarms")
        assertTrue(DESERT_SPECIES.any { it.squad == Squad.BURROW }, "nothing comes up out of the sand")
        assertTrue(DESERT_SPECIES.any { it.movement == EnemyMovementType.LOOP }, "nothing loops")
        assertTrue(DESERT_SPECIES.any { it.gun != null }, "nothing shoots")
        assertTrue(DESERT_SPECIES.any { it.innateShield > 0f && it.shieldRegrowth > 0f }, "no shell grows back")
    }

    /** A sprite that flies an arc has to turn to follow it, or it flies the arc sideways. */
    @Test
    fun `the ones that fly arcs turn to face along them`() {
        assertTrue(EnemySpecies.HAWK.facesHeading)
        assertTrue(EnemySpecies.WYRMLING.facesHeading)
        assertTrue(EnemySpecies.entries.filter { it.facesHeading }.all {
            it.movement == EnemyMovementType.LOOP || it.movement == EnemyMovementType.LEAP
        })
    }
}
