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

    /** Surviving is worth something on its own; this is the original score. */
    fun awardSurvivalTick() {
        score += 1
    }

    /** Extends the streak first, so a kill scores at the multiplier it just earned. */
    fun registerEnemyDestroyed() {
        hitStreak++
        score += pointsPerHit * multiplier
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
        score += levelCompleteBonus
    }

    fun reset() {
        score = 0
        hitStreak = 0
    }
}
