package at.smiech.cyanbat.progress

import at.smiech.cyanbat.util.AURA_FULL_INTENSITY_LEVEL
import at.smiech.cyanbat.util.AURA_LEVELS_PER_TIER
import at.smiech.cyanbat.util.ELITE_EXPERIENCE_FACTOR
import at.smiech.cyanbat.util.XP_FIRST_LEVEL
import at.smiech.cyanbat.util.XP_LEVEL_STEP
import at.smiech.cyanbat.util.XP_PER_KILL
import at.smiech.cyanbat.util.XP_PER_KILL_PER_WAVE

/**
 * The bat's experience over a single run, and the levels it buys.
 *
 * Not to be confused with the [at.smiech.cyanbat.resource.Stage] being flown or its
 * waves: this is the player getting stronger, that is the stage getting harder, and
 * the two curves race each other.
 *
 * Experience is per run and never persisted.
 *
 * @param firstLevelCost experience needed to go from level 1 to level 2.
 * @param levelStep how much more each level costs than the one before it.
 */
class PlayerProgress(
    private val firstLevelCost: Int = XP_FIRST_LEVEL,
    private val levelStep: Int = XP_LEVEL_STEP,
) {
    /** Levels are 1-based: the bat starts the run at level 1, having earned nothing yet. */
    var level: Int = 1
        private set

    /** Experience banked toward the *next* level, not since the start of the run. */
    var experience: Int = 0
        private set

    /** Total experience earned this run, which is what the run is actually worth. */
    var totalExperience: Int = 0
        private set

    /** What [experience] has to reach for the next level. */
    val experienceForNextLevel: Int
        get() = costOfLevel(level)

    /** Progress toward the next level, as 0..1, for a bar to be drawn from. */
    val fraction: Float
        get() = (experience.toFloat() / experienceForNextLevel).coerceIn(0f, 1f)

    /**
     * How strongly the bat glows, 0..1, for its `AuraComponent`: nothing at level 1, full at
     * [AURA_FULL_INTENSITY_LEVEL], rising evenly between. Derived from the level alone, so it lives
     * here.
     */
    val auraIntensity: Float
        get() = ((level - 1f) / (AURA_FULL_INTENSITY_LEVEL - 1f)).coerceIn(0f, 1f)

    /**
     * Which tier of the aura the level has reached: the sparks and lightning, which
     * arrive in steps.
     *
     * Zero until the first [AURA_LEVELS_PER_TIER] levels, so a run opens with only the glow.
     * Uncapped; the aura system applies its own ceilings.
     */
    val auraTier: Int
        get() = level / AURA_LEVELS_PER_TIER

    /**
     * Banks [amount] and returns how many levels it bought: one kill late in a run can cross
     * several thresholds, each owed a power-up. The remainder carries over.
     */
    fun award(amount: Int): Int {
        if (amount <= 0) return 0
        experience += amount
        totalExperience += amount

        var levelsGained = 0
        while (experience >= experienceForNextLevel) {
            experience -= experienceForNextLevel
            level++
            levelsGained++
        }
        return levelsGained
    }

    /** Back to level 1 with nothing earned. */
    fun reset() {
        level = 1
        experience = 0
        totalExperience = 0
    }

    /** What it costs to leave [level] for the next one. Each level is [levelStep] dearer. */
    private fun costOfLevel(level: Int): Int = firstLevelCost + (level - 1) * levelStep

    companion object {
        /**
         * What killing an enemy from [waveIndex] is worth, times [ELITE_EXPERIENCE_FACTOR] for an
         * [elite]. Scaled by the wave so pressing on pays better than farming the opening minute.
         */
        fun experienceForKill(waveIndex: Int, elite: Boolean = false): Int =
            (XP_PER_KILL + waveIndex * XP_PER_KILL_PER_WAVE) * if (elite) ELITE_EXPERIENCE_FACTOR else 1
    }
}
