package at.smiech.cyanbat.service

import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.util.ENDLESS_DIFFICULTY_PER_ROUND
import at.smiech.cyanbat.util.ENDLESS_OPENING_DIFFICULTY
import at.smiech.cyanbat.util.ENDLESS_ROUND_WAVES
import at.smiech.cyanbat.util.NAGA_VITALITY
import at.smiech.cyanbat.util.SKY_VIPER_VITALITY
import kotlin.random.Random

/**
 * The endless stage's rounds, dealt one after another for as long as the bat lasts: each a few
 * minute-long waves of enemies from every stage, mixed afresh, then a boss from every stage's.
 *
 * Each round is handed out as the [StageProgression] the generator runs it on (see
 * [EnemyGenerator.beginRound]), so a round is flown exactly as a stage is, and only what it is made
 * of changes:
 * - **Its waves** draw two to four species from the whole bestiary, more as the run goes on, never
 *   two swarms, two formations or two things from below or behind at once, so every wave can still
 *   be read. Shields, guns and elites ramp with the wave's number through the whole run.
 * - **Its boss** comes out of a shuffled bag of all five, so every boss is met before any is met
 *   twice, and never the same one twice running.
 * - **Its strength** opens where the cave's does and climbs [ENDLESS_DIFFICULTY_PER_ROUND] a round,
 *   on top of its waves being numbered on from the last round's (see
 *   [StageProgression.wavesBefore]): every round is harder than the one before, with no ceiling.
 *
 * @param random the course's own dice, so the rounds are the same for the same seed.
 * @param playerDifficulty thins out every round's waves, as it does a stage's.
 */
class EndlessCourse(
    private val random: Random,
    private val playerDifficulty: Difficulty = Difficulty.HARD,
) {
    /** How many rounds have been dealt. */
    var rounds = 0
        private set

    /** Waves dealt before the next round's first. */
    private var wavesDealt = 0

    /** The bosses still to come before every one has been met again. */
    private val bossBag = ArrayList<BossKind>(BossKind.entries.size)
    private var lastBoss: BossKind? = null

    /** The next round, its waves numbered on from the last round's. */
    fun next(): StageProgression {
        val round = rounds++
        val waves = List(ENDLESS_ROUND_WAVES) { waveDesign(wavesDealt + it) }
        val boss = nextBoss()
        val difficulty = difficultyOf(round)
        val design = StageDesign(waves = waves, boss = boss, bossVitality = vitalityOf(boss))
        return StageProgression(
            design = design,
            bossWave = ENDLESS_ROUND_WAVES,
            difficulty = difficulty,
            bossDifficulty = difficulty,
            enemyDensity = playerDifficulty.enemyDensity,
            wavesBefore = wavesDealt,
        ).also { wavesDealt += ENDLESS_ROUND_WAVES }
    }

    /**
     * Wave [number] of the run, from 0: a fresh mix of species, with shields, guns and elites as
     * likely as the wave is far in. The run's first wave is two species with no extras, to find
     * one's feet in.
     */
    private fun waveDesign(number: Int): WaveDesign {
        val count = 2 + (if (number >= 3) 1 else 0) + (if (number >= 8) 1 else 0)
        val species = ArrayList<EnemySpecies>(count)
        while (species.size < count) {
            val candidate = SPECIES[random.nextInt(SPECIES.size)]
            if (candidate in species || species.any { clashes(it, candidate) }) continue
            species += candidate
        }
        return WaveDesign(
            species = species,
            shieldChance = (0.04f + 0.03f * number).coerceAtMost(MAX_SHIELD_CHANCE),
            gunChance = (0.03f * number).coerceAtMost(MAX_GUN_CHANCE),
            eliteChance = if (number == 0) 0f else (0.03f + 0.005f * number).coerceAtMost(MAX_ELITE_CHANCE),
        )
    }

    /** The next boss out of the bag, refilled and shuffled once it is empty. */
    private fun nextBoss(): BossKind {
        if (bossBag.isEmpty()) {
            bossBag += BossKind.entries
            bossBag.shuffle(random)
            // A fresh bag must not open on the boss that closed the last one.
            if (bossBag.first() == lastBoss) bossBag.add(bossBag.removeAt(0))
        }
        return bossBag.removeAt(0).also { lastBoss = it }
    }

    companion object {
        /** Every species the endless stage can send, from every stage. */
        private val SPECIES = EnemySpecies.entries

        private const val MAX_SHIELD_CHANCE = 0.45f
        private const val MAX_GUN_CHANCE = 0.4f
        private const val MAX_ELITE_CHANCE = 0.1f

        /** Round [round]'s strength, from 0, against stage 1's. */
        fun difficultyOf(round: Int): Float =
            ENDLESS_OPENING_DIFFICULTY + round * ENDLESS_DIFFICULTY_PER_ROUND

        /**
         * A boss's health against an ordinary one's: the Naga's and the Sky Viper's longer fights
         * keep the extra health their stage gives them.
         */
        fun vitalityOf(boss: BossKind): Float = when (boss) {
            BossKind.NAGA -> NAGA_VITALITY
            BossKind.SKY_VIPER -> SKY_VIPER_VITALITY
            else -> 1f
        }

        /**
         * Whether two species would make a wave that cannot be read: two swarms or two formations
         * at once, or two things coming from below or behind.
         */
        private fun clashes(a: EnemySpecies, b: EnemySpecies): Boolean {
            val low = setOf(Squad.BURROW, Squad.FROM_BEHIND)
            return a.squad == b.squad && a.squad != Squad.SOLO || a.squad in low && b.squad in low
        }
    }
}
