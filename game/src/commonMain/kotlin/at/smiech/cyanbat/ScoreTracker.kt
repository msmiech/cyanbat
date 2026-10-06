package at.smiech.cyanbat

import at.smiech.cyanbat.util.ELITE_SCORE_FACTOR
import at.smiech.cyanbat.util.HITS_PER_MULTIPLIER_STEP
import at.smiech.cyanbat.util.POINTS_PER_HIT
import at.smiech.cyanbat.util.STAGE_COMPLETE_BONUS

/**
 * Scoring for a single run: points for every enemy destroyed, multiplied by the current kill
 * streak, and a lump sum for clearing the stage. Being hit resets the streak.
 *
 * Kept apart from the game screen so scoring can be tested without a live game and assets.
 */
class ScoreTracker(
    private val pointsPerHit: Int = POINTS_PER_HIT,
    private val hitsPerMultiplierStep: Int = HITS_PER_MULTIPLIER_STEP,
    private val stageCompleteBonus: Int = STAGE_COMPLETE_BONUS,
    private val eliteFactor: Int = ELITE_SCORE_FACTOR,
) {
    var score: Int = 0
        private set

    /** Enemies destroyed since the bat was last hit. */
    var hitStreak: Int = 0
        private set

    /**
     * One step per [hitsPerMultiplierStep] unbroken kills, with no ceiling, so a long streak always
     * stays worth keeping.
     */
    val multiplier: Int
        get() = 1 + hitStreak / hitsPerMultiplierStep

    /**
     * Multiplies everything scored, for power-ups that pay in points. Set by the screen from the
     * run's loadout; 1 is unmodified.
     */
    var bonusMultiplier: Float = 1f

    /**
     * Fractional points not yet banked. A bonus of a few percent on a kill is a fraction of a
     * point; carrying the remainder makes it pay in full instead of being rounded away.
     */
    private var remainder: Float = 0f

    /**
     * Scores a kill. Extends the streak first, so the kill scores at the multiplier it just earned.
     *
     * @param elite whether it was an elite, which pays [eliteFactor] kills' worth of points but
     *   still counts as one kill to the streak.
     */
    fun registerEnemyDestroyed(elite: Boolean = false) {
        hitStreak++
        award(pointsPerHit * multiplier * if (elite) eliteFactor else 1)
    }

    /** The bat took damage. Points already banked stay; the streak does not. */
    fun registerPlayerHit() {
        hitStreak = 0
    }

    /**
     * The stage's boss is down. Worth two hundred kills at the base rate, so pushing on to the boss
     * beats farming the early waves.
     */
    fun awardStageCleared() {
        award(stageCompleteBonus)
    }

    /**
     * Banks [points] at the run's [bonusMultiplier], carrying the fraction over.
     *
     * Saturates at Int.MAX_VALUE rather than wrapping negative; no run comes near it, but the
     * multiplier is uncapped.
     */
    private fun award(points: Int) {
        val earned = points * bonusMultiplier + remainder
        val banked = earned.toInt()
        score = (score.toLong() + banked).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        remainder = earned - banked
    }

    /** Clears the score and the streak, as at the start of a run. */
    fun reset() {
        score = 0
        hitStreak = 0
        remainder = 0f
    }
}
