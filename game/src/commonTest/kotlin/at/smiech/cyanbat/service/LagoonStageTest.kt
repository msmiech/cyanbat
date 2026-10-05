package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.BOSS_WAVE
import at.smiech.cyanbat.util.NAGA_PHASE_2_AT
import at.smiech.cyanbat.util.NAGA_PHASE_3_AT
import at.smiech.cyanbat.util.NAGA_PHASE_4_AT
import at.smiech.cyanbat.util.NAGA_PHASE_5_AT
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import at.smiech.engine.ecs.EnemyMovementType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val MINUTE = WAVE_DURATION_SECONDS

private val LAGOON_SPECIES = setOf(
    EnemySpecies.PIRANHA, EnemySpecies.CRAB, EnemySpecies.SHARK, EnemySpecies.PUFFER, EnemySpecies.KRAIT,
)

/** Stage 4 as designed: what it sends, how hard, in what order, and the Naga at the end of it. */
class LagoonStageTest {

    private val desert = StageProgression.forStage(3)
    private val lagoon = StageProgression.forStage(4)

    private fun waves(stage: StageProgression) = (0 until BOSS_WAVE).map { stage.waveAt(it * MINUTE) }

    @Test
    fun `stage 4 is the lagoon with five waves and the Naga`() {
        assertEquals(StageDesign.LAGOON, lagoon.design)
        assertEquals(5, lagoon.design.waves.size)
        assertEquals(5, lagoon.bossWave)
        assertEquals(BossKind.NAGA, lagoon.design.boss)
    }

    /** Past the last stage its design repeats, harder, rather than falling back to an earlier one. */
    @Test
    fun `a stage past the lagoon is the lagoon again`() {
        assertEquals(StageDesign.LAGOON, StageDesign.forStage(5))
    }

    @Test
    fun `the lagoon sends only its own enemies and all five of them`() {
        assertEquals(LAGOON_SPECIES, waves(lagoon).flatMap { it.enemyTypes }.toSet())
    }

    @Test
    fun `no two lagoon waves in a row send the same mix`() {
        waves(lagoon).map { it.enemyTypes }.zipWithNext { earlier, later -> assertNotEquals(earlier, later) }
    }

    /** The hardest stage in the game, wave for wave. */
    @Test
    fun `every lagoon wave is tougher than the same wave in the desert`() {
        waves(desert).zip(waves(lagoon)).forEach { (desert, lagoon) ->
            assertTrue(lagoon.hitPoints > desert.hitPoints, "wave ${lagoon.index}: health")
            assertTrue(lagoon.damage > desert.damage, "wave ${lagoon.index}: damage")
            assertTrue(lagoon.spawnIntervalSeconds <= desert.spawnIntervalSeconds, "wave ${lagoon.index}: pace")
            assertTrue(lagoon.shieldChance >= desert.shieldChance, "wave ${lagoon.index}: shields")
            assertTrue(lagoon.gunChance >= desert.gunChance, "wave ${lagoon.index}: guns")
            assertTrue(lagoon.eliteChance >= desert.eliteChance, "wave ${lagoon.index}: elites")
        }
    }

    @Test
    fun `shields and guns ramp in over the stage and never back off`() {
        val lagoonWaves = waves(lagoon)

        lagoonWaves.zipWithNext { earlier, later ->
            assertTrue(later.shieldChance >= earlier.shieldChance, "shields backed off into wave ${later.index}")
            assertTrue(later.gunChance >= earlier.gunChance, "guns backed off into wave ${later.index}")
        }
        assertEquals(0f, lagoonWaves.first().shieldChance)
        assertEquals(0f, lagoonWaves.first().gunChance)
    }

    /** Something new to watch is taught before everything is thrown at the player at once. */
    @Test
    fun `nothing comes from behind in the opening minute`() {
        assertTrue(waves(lagoon).first().enemyTypes.none { it.squad == Squad.FROM_BEHIND })
        assertTrue(waves(lagoon)[1].enemyTypes.any { it.squad == Squad.FROM_BEHIND }, "the sharks never arrive early")
    }

    @Test
    fun `the lagoon has a school and a shell and something from behind and a ring of spines and a brood`() {
        assertTrue(LAGOON_SPECIES.any { it.squad == Squad.SWARM }, "nothing schools")
        assertTrue(LAGOON_SPECIES.any { it.innateShield > 0f && it.shieldRegrowth > 0f }, "no shell grows back")
        assertTrue(LAGOON_SPECIES.any { it.squad == Squad.FROM_BEHIND && it.drawnFacingRight }, "nothing comes from behind")
        assertTrue(
            LAGOON_SPECIES.any { species -> species.gun?.volleys?.any { it.pattern == at.smiech.cyanbat.ecs.ShotPattern.RADIAL } == true },
            "nothing throws a ring",
        )
        assertTrue(LAGOON_SPECIES.any { it.movement == EnemyMovementType.SINE && it.facesHeading }, "nothing weaves")
    }

    /** The one that comes from behind is the only one drawn facing the way it flies, right. */
    @Test
    fun `only what comes from behind is drawn facing right`() {
        assertEquals(setOf(EnemySpecies.SHARK), EnemySpecies.entries.filter { it.drawnFacingRight }.toSet())
        assertTrue(EnemySpecies.SHARK.speedX > 0f, "it does not fly right")
    }

    /** The longest fight in the game: more health than any boss before it, over five phases. */
    @Test
    fun `the Naga outlasts every boss before it`() {
        val naga = lagoon.bossWave().hitPoints
        for (stage in 1..3) {
            val before = StageProgression.forStage(stage).bossWave().hitPoints
            assertTrue(naga > before * 2, "the Naga's $naga against stage $stage's $before")
        }
        // Its blows are its stage's, though: the health is the fight's length, not its danger.
        assertEquals(lagoon.bossWave().damage, (50 * lagoon.difficulty).toInt())
    }

    @Test
    fun `the Naga has five phases of a fifth of its health apiece`() {
        assertEquals(1, NagaBrain.phaseFor(1f))
        assertEquals(2, NagaBrain.phaseFor(NAGA_PHASE_2_AT))
        assertEquals(3, NagaBrain.phaseFor(NAGA_PHASE_3_AT))
        assertEquals(4, NagaBrain.phaseFor(NAGA_PHASE_4_AT))
        assertEquals(5, NagaBrain.phaseFor(NAGA_PHASE_5_AT))
        assertEquals(5, NagaBrain.phaseFor(0.01f))
        assertEquals(listOf(0, 1, 1, 2, 3), (1..5).map { NagaBrain.strikesFor(it) })
    }
}
