package at.smiech.cyanbat

import at.smiech.cyanbat.util.HITS_PER_MULTIPLIER_STEP
import at.smiech.cyanbat.util.LEVEL_COMPLETE_BONUS
import at.smiech.cyanbat.util.MAX_SCORE_MULTIPLIER
import at.smiech.cyanbat.util.POINTS_PER_HIT

/**
 * Scoring for a single run.
 *
 * Three sources: a point for every tick survived, a bonus for every enemy destroyed, and a lump
 * sum for clearing the level. The kill bonus scales with an unbroken run of kills, so the reward
 * for pressing forward is losing the streak the moment the bat is hit.
 *
 * Kept apart from the game screen because it is the one part of scoring worth testing on its
 * own - the screen itself needs a live Game and asset set.
 */
class ScoreTracker(
    private val pointsPerHit: Int = POINTS_PER_HIT,
    private val hitsPerMultiplierStep: Int = HITS_PER_MULTIPLIER_STEP,
    private val maxMultiplier: Int = MAX_SCORE_MULTIPLIER,
    private val levelCompleteBonus: Int = LEVEL_COMPLETE_BONUS,
) {
    var score: Int = 0
        private set

    /** Enemies destroyed since the bat was last hit. */
    var hitStreak: Int = 0
        private set

    /** One step per [hitsPerMultiplierStep] unbroken kills, capped so it cannot run away. */
    val multiplier: Int
        get() = (1 + hitStreak / hitsPerMultiplierStep).coerceAtMost(maxMultiplier)

    /**
     * Everything scored is multiplied by this, for the power-ups that pay in points. Set by the
     * screen from the run's loadout; 1 is unmodified.
     */
    var bonusMultiplier: Float = 1f

    /**
     * Points earned but too small to bank yet.
     *
     * Surviving pays a single point a tick, so a ten percent bonus on it is a tenth of a point:
     * rounded per award it would vanish every time and the power-up would do nothing at all on the
     * largest source of score in the game. Carrying the remainder is what makes it pay.
     */
    private var remainder: Float = 0f

    /** Surviving is worth something on its own; this is the original score. */
    fun awardSurvivalTick() {
        award(1)
    }

    /** Extends the streak first, so a kill scores at the multiplier it just earned. */
    fun registerEnemyDestroyed() {
        hitStreak++
        award(pointsPerHit * multiplier)
    }

    /** The bat took damage. Points already banked stay; the streak does not. */
    fun registerPlayerHit() {
        hitStreak = 0
    }

    /**
     * The level's boss is down. Worth roughly three minutes of surviving on its own, so that
     * pushing on to the boss beats farming the early waves for survival ticks.
     */
    fun awardLevelCleared() {
        award(levelCompleteBonus)
    }

    /** Banks [points] at the run's [bonusMultiplier], keeping what is left over for next time. */
    private fun award(points: Int) {
        val earned = points * bonusMultiplier + remainder
        val banked = earned.toInt()
        score += banked
        remainder = earned - banked
    }

    fun reset() {
        score = 0
        hitStreak = 0
        remainder = 0f
    }
}
