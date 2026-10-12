package at.smiech.cyanbat

import at.smiech.cyanbat.progress.PlayerLoadout
import at.smiech.cyanbat.progress.PowerUp
import at.smiech.cyanbat.service.StageProgression
import at.smiech.cyanbat.util.BOSS_WAVE
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.PLAYER_MAX_HIT_POINTS
import at.smiech.cyanbat.util.REGEN_PER_SECOND
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What each [Difficulty] changes about a run, and how the stages keep the hardest one cleared. */
class DifficultyTest {

    @Test
    fun `Hard is the game as it is tuned`() {
        for (stageId in 1..4) {
            assertEquals(
                StageProgression.forStage(stageId),
                StageProgression.forStage(stageId, Difficulty.HARD),
            )
        }
        val loadout = PlayerLoadout(Difficulty.HARD)
        assertEquals(PLAYER_MAX_HIT_POINTS, loadout.maxHitPoints)
        assertEquals(DAMAGE_PER_HIT, loadout.shotDamage)
        assertEquals(1f, loadout.experienceMultiplier)
        assertEquals(0, loadout.revives, "no Second Life on Hard")
        assertEquals(0f, loadout.healthRegenPerSecond, "no Regeneration on Hard")
    }

    @Test
    fun `Easy starts with Second Life and Regeneration and Normal with Regeneration`() {
        val easy = PlayerLoadout(Difficulty.EASY)
        assertEquals(1, easy.revives)
        assertEquals(REGEN_PER_SECOND.toFloat(), easy.healthRegenPerSecond)

        val normal = PlayerLoadout(Difficulty.NORMAL)
        assertEquals(0, normal.revives)
        assertEquals(REGEN_PER_SECOND.toFloat(), normal.healthRegenPerSecond)
    }

    /** A power-up held from the start stacks with the same one picked later, as a pick would. */
    @Test
    fun `a power-up held from the start can still be picked again`() {
        val easy = PlayerLoadout(Difficulty.EASY)
        assertTrue(PowerUp.SECOND_LIFE.isAvailable(easy))
        assertTrue(PowerUp.REGENERATION.isAvailable(easy))
        PowerUp.REGENERATION.applyTo(easy)
        assertEquals(2f * REGEN_PER_SECOND, easy.healthRegenPerSecond)
    }

    /** Each step down sends fewer enemies and makes more of the bat, in every respect at once. */
    @Test
    fun `every easier difficulty is easier in every way than the one above it`() {
        Difficulty.entries.zipWithNext { easier, harder ->
            assertTrue(easier.enemyDensity < harder.enemyDensity, "$easier is no sparser")
            assertTrue(easier.experienceFactor > harder.experienceFactor, "$easier pays no more")
            val easierBat = PlayerLoadout(easier)
            val harderBat = PlayerLoadout(harder)
            assertTrue(easierBat.maxHitPoints > harderBat.maxHitPoints, "$easier is no tougher")
            assertTrue(easierBat.shotDamage > harderBat.shotDamage, "$easier hits no harder")
            assertTrue(
                easierBat.experienceMultiplier > harderBat.experienceMultiplier,
                "$easier learns no faster",
            )
        }
    }

    /** One more hit than Hard's three on Normal, and two more on Easy. */
    @Test
    fun `an easier bat takes more of the last wave's hits`() {
        val hit = StageProgression.forStage(1).waveAt((BOSS_WAVE - 1) * WAVE_DURATION_SECONDS).damage
        fun hitsToFall(difficulty: Difficulty) =
            (PlayerLoadout(difficulty).maxHitPoints + hit - 1) / hit
        assertEquals(3, hitsToFall(Difficulty.HARD))
        assertEquals(4, hitsToFall(Difficulty.NORMAL))
        assertEquals(5, hitsToFall(Difficulty.EASY))
    }

    @Test
    fun `fewer enemies arrive on an easier difficulty but none of them is weaker`() {
        for (stageId in 1..4) {
            val hard = StageProgression.forStage(stageId, Difficulty.HARD)
            for (difficulty in listOf(Difficulty.NORMAL, Difficulty.EASY)) {
                val easier = StageProgression.forStage(stageId, difficulty)
                for (wave in 0 until BOSS_WAVE) {
                    val seconds = wave * WAVE_DURATION_SECONDS + 30f
                    assertEquals(
                        hard.spawnIntervalAt(seconds) / difficulty.enemyDensity,
                        easier.spawnIntervalAt(seconds),
                        0.0001f,
                    )
                    val hardWave = hard.waveAt(seconds)
                    val easierWave = easier.waveAt(seconds)
                    assertEquals(hardWave.hitPoints, easierWave.hitPoints)
                    assertEquals(hardWave.damage, easierWave.damage)
                    assertEquals(hardWave.enemyTypes, easierWave.enemyTypes)
                }
                assertEquals(hard.bossWave().hitPoints, easier.bossWave().hitPoints)
                assertEquals(hard.bossTimeSeconds, easier.bossTimeSeconds)
            }
        }
    }

    @Test
    fun `stars run from one on Easy to three on Hard`() {
        assertEquals(listOf(1, 2, 3), Difficulty.entries.map { it.stars })
        for (difficulty in Difficulty.entries) {
            assertEquals(difficulty, Difficulty.ofStars(difficulty.stars))
        }
        assertNull(Difficulty.ofStars(0))
        assertNull(Difficulty.ofStars(4))
    }

    @Test
    fun `a stage keeps the hardest difficulty it was cleared on`() = runTest {
        val store = StageUnlockStore.InMemory()
        store.recordClearAsync(1, Difficulty.NORMAL)
        store.recordClearAsync(1, Difficulty.EASY)
        assertEquals(mapOf(1 to Difficulty.NORMAL), store.clears.first())
        store.recordClearAsync(1, Difficulty.HARD)
        store.recordClearAsync(2, Difficulty.EASY)
        assertEquals(mapOf(1 to Difficulty.HARD, 2 to Difficulty.EASY), store.clears.first())
    }

    /** Before difficulties, every stage was flown as Hard is now, and only the unlocks were kept. */
    @Test
    fun `the stages opened before there were difficulties were cleared on Hard`() {
        assertEquals(emptyMap(), clearsBeforeDifficulties(1))
        assertEquals(
            mapOf(1 to Difficulty.HARD, 2 to Difficulty.HARD, 3 to Difficulty.HARD),
            clearsBeforeDifficulties(4),
        )
    }
}
