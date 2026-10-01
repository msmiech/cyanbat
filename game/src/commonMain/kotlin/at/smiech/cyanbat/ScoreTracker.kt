package at.smiech.cyanbat

import at.smiech.cyanbat.util.ELITE_SCORE_FACTOR
import at.smiech.cyanbat.util.HITS_PER_MULTIPLIER_STEP
import at.smiech.cyanbat.util.POINTS_PER_HIT
import at.smiech.cyanbat.util.STAGE_COMPLETE_BONUS

/**
 * Scoring for a single run.
 *
 * Two sources: a bonus for every enemy destroyed, and a lump sum for clearing the stage. The kill
 * bonus scales with an unbroken run of kills, so the reward for pressing forward is losing the
 * streak the moment the bat is hit.
 *
 * Kept apart from the game screen because it is the one part of scoring worth testing on its
 * own - the screen itself needs a live Game and asset set.
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
     * One step per [hitsPerMultiplierStep] unbroken kills, with no ceiling. A long streak is the
     * hardest thing in the game to keep, and a number that stops climbing is a reason to stop
     * caring about it.
     */
    val multiplier: Int
        get() = 1 + hitStreak / hitsPerMultiplierStep

    /**
     * Everything scored is multiplied by this, for the power-ups that pay in points. Set by the
     * screen from the run's loadout; 1 is unmodified.
     */
    var bonusMultiplier: Float = 1f

    /**
     * Points earned but too small to bank yet.
     *
     * A bonus of a few percent on a kill comes to a fraction of a point, and rounded away on every
     * award it would add up to less than the power-up promised. Carrying the remainder is what
     * makes it pay in full.
     */
    private var remainder: Float = 0f

    /**
     * Extends the streak first, so a kill scores at the multiplier it just earned.
     *
     * @param elite whether it was an elite, which pays [eliteFactor] kills' worth of points - and is
     *   still one kill to the streak, which counts how long the bat has gone untouched rather than
     *   what it earned in that time.
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
     * The stage's boss is down. Worth two hundred kills at the base rate on its own, so that pushing
     * on to the boss beats farming the early waves.
     */
    fun awardStageCleared() {
        award(stageCompleteBonus)
    }

    /**
     * Banks [points] at the run's [bonusMultiplier], keeping what is left over for next time.
     *
     * Held at the largest score there is rather than wrapping round to a negative one. No run comes
     * near it, but with the multiplier uncapped nothing but the length of a streak says so.
     */
    private fun award(points: Int) {
        val earned = points * bonusMultiplier + remainder
        val banked = earned.toInt()
        score = (score.toLong() + banked).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        remainder = earned - banked
    }

    fun reset() {
        score = 0
        hitStreak = 0
        remainder = 0f
    }
}
