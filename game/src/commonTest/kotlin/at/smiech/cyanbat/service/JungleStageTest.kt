package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.BOSS_WAVE
import at.smiech.cyanbat.util.STAGE_DIFFICULTY_STEP
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val MINUTE = WAVE_DURATION_SECONDS

private val JUNGLE_SPECIES = setOf(
    EnemySpecies.WASP, EnemySpecies.BEETLE, EnemySpecies.SPITTER, EnemySpecies.OWL, EnemySpecies.WISP,
)

/** Stage 1 as designed, and the cave after it: what each sends, how hard, and in what order. */
class JungleStageTest {

    private val jungle = StageProgression.forStage(1)
    private val cave = StageProgression.forStage(2)

    private fun waves(stage: StageProgression) = (0 until BOSS_WAVE).map { stage.waveAt(it * MINUTE) }

    @Test
    fun `stage 1 is the jungle with five waves and the Moth Queen`() {
        assertEquals(StageDesign.JUNGLE, jungle.design)
        assertEquals(5, jungle.design.waves.size)
        assertEquals(5, jungle.bossWave)
        assertEquals(BossKind.MOTH_QUEEN, jungle.design.boss)
    }

    @Test
    fun `stage 2 is the cave with five waves and the Caco Imp`() {
        assertEquals(StageDesign.CAVE, cave.design)
        assertEquals(5, cave.design.waves.size)
        assertEquals(BossKind.CACO_IMP, cave.design.boss)
    }

    @Test
    fun `the jungle sends only its own enemies and all five of them`() {
        val sent = waves(jungle).flatMap { it.enemyTypes }.toSet()

        assertEquals(JUNGLE_SPECIES, sent)
    }

    @Test
    fun `no two jungle waves in a row send the same mix`() {
        waves(jungle).map { it.enemyTypes }.zipWithNext { earlier, later ->
            assertNotEquals(earlier, later)
        }
    }

    /** "More difficult" on every axis a wave has, wave for wave. */
    @Test
    fun `every cave wave is tougher than the same wave in the jungle`() {
        waves(jungle).zip(waves(cave)).forEach { (jungle, cave) ->
            assertTrue(cave.hitPoints > jungle.hitPoints, "wave ${cave.index}: health")
            assertTrue(cave.damage > jungle.damage, "wave ${cave.index}: damage")
            assertTrue(cave.spawnIntervalSeconds < jungle.spawnIntervalSeconds, "wave ${cave.index}: pace")
        }
    }

    /** The jungle's waves are eased for an opening stage; the Moth Queen is not. */
    @Test
    fun `the Moth Queen is as hard as she was as the second stage's boss`() {
        val asSecond = StageProgression(design = StageDesign.JUNGLE, difficulty = 1f + STAGE_DIFFICULTY_STEP)

        assertEquals(1f, jungle.difficulty)
        assertEquals(asSecond.bossWave().hitPoints, jungle.bossWave().hitPoints)
        assertEquals(asSecond.bossWave().damage, jungle.bossWave().damage)
        assertEquals(asSecond.bossShotsToKill, jungle.bossShotsToKill)
    }

    @Test
    fun `her swarms come at her strength rather than the stage's`() {
        val asSecond = StageProgression(design = StageDesign.JUNGLE, difficulty = 1f + STAGE_DIFFICULTY_STEP)
        val lastWave = jungle.waveAt(jungle.bossTimeSeconds - 1f)

        assertEquals(asSecond.waveAt(asSecond.bossTimeSeconds - 1f), jungle.escortWave())
        assertTrue(jungle.escortWave().hitPoints > lastWave.hitPoints)
    }

    @Test
    fun `a boss scaled like its stage is summoned for by its stage's last wave`() {
        assertEquals(cave.waveAt(cave.bossTimeSeconds - 1f), cave.escortWave())
    }

    /** Shields and guns are taught before they are everywhere. */
    @Test
    fun `shields and guns ramp in over the stage and never back off`() {
        val jungleWaves = waves(jungle)

        jungleWaves.zipWithNext { earlier, later ->
            assertTrue(later.shieldChance >= earlier.shieldChance, "shields backed off into wave ${later.index}")
            assertTrue(later.gunChance >= earlier.gunChance, "guns backed off into wave ${later.index}")
        }
        assertTrue(jungleWaves.last().shieldChance > 0f)
        assertTrue(jungleWaves.last().gunChance > 0f)
    }

    /**
     * The first stage a player flies: the opening two minutes are its own enemies as they come, with
     * nothing handed a shield or a gun on top of what it carries anyway.
     */
    @Test
    fun `nothing is handed a shield or a gun in the jungle's first two minutes`() {
        waves(jungle).take(2).forEach {
            assertEquals(0f, it.shieldChance, "wave ${it.index}: shields")
            assertEquals(0f, it.gunChance, "wave ${it.index}: guns")
        }
    }

    @Test
    fun `the cave hands out no shields and no guns`() {
        waves(cave).forEach {
            assertEquals(0f, it.shieldChance)
            assertEquals(0f, it.gunChance)
        }
    }

    @Test
    fun `the jungle has something that shoots and something shielded and groups`() {
        assertTrue(JUNGLE_SPECIES.any { it.gun != null }, "nothing shoots")
        assertTrue(JUNGLE_SPECIES.any { it.innateShield > 0f }, "nothing is shielded")
        assertTrue(JUNGLE_SPECIES.any { it.squad == Squad.SWARM }, "nothing swarms")
        assertTrue(JUNGLE_SPECIES.any { it.squad == Squad.V_FORMATION }, "nothing flies in formation")
    }
}
