package at.smiech.cyanbat.progress

import at.smiech.cyanbat.util.AURA_FULL_INTENSITY_LEVEL
import at.smiech.cyanbat.util.AURA_LEVELS_PER_TIER
import at.smiech.cyanbat.util.XP_FIRST_LEVEL
import at.smiech.cyanbat.util.XP_LEVEL_STEP
import at.smiech.cyanbat.util.XP_PER_KILL
import at.smiech.cyanbat.util.XP_PER_KILL_PER_WAVE

/**
 * The bat's experience over a single run, and the levels it buys.
 *
 * Not to be confused with [at.smiech.cyanbat.resource.Level], which is the stage being flown
 * through, or with the waves inside it. This is the player getting stronger; that is the cave
 * getting harder. They are deliberately separate curves, because the whole point of the two is
 * that they race each other.
 *
 * Experience is per-run and dies with the bat. Nothing here is persisted: a level bought with a
 * power-up would be worth nothing if the next run started with it already spent.
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

    /** Experience banked towards the *next* level, not since the start of the run. */
    var experience: Int = 0
        private set

    /** Total experience earned this run, which is what the run is actually worth. */
    var totalExperience: Int = 0
        private set

    /** What [experience] has to reach for the next level. */
    val experienceForNextLevel: Int
        get() = costOfLevel(level)

    /** Progress towards the next level, as 0..1, for a bar to be drawn from. */
    val fraction: Float
        get() = (experience.toFloat() / experienceForNextLevel).coerceIn(0f, 1f)

    /**
     * How hard the bat is glowing, as 0..1, for an `AuraComponent` to be set from.
     *
     * Nothing at level 1 and full at [AURA_FULL_INTENSITY_LEVEL], climbing evenly in between. It
     * lives here rather than in the screen that draws it because it is a reading of the level and
     * nothing else: the halo *is* how far the run has come, which is the same thing this class
     * exists to count.
     */
    val auraIntensity: Float
        get() = ((level - 1f) / (AURA_FULL_INTENSITY_LEVEL - 1f)).coerceIn(0f, 1f)

    /**
     * Which band of the aura the level has reached - the sparks and the lightning, which arrive a
     * step at a time rather than growing.
     *
     * Zero until the first whole [AURA_LEVELS_PER_TIER] levels are in, so the opening of a run has
     * a glow and nothing else. Uncapped: the effect's own ceilings are the system's business, not
     * this one's, and a run that gets to level 60 has earned whatever it can be given.
     */
    val auraTier: Int
        get() = level / AURA_LEVELS_PER_TIER

    /**
     * Banks [amount] and reports how many levels it bought.
     *
     * The return is a count rather than a flag because a single kill late in a run can cross more
     * than one threshold, and the player is owed a power-up for each. The remainder carries over,
     * so nothing earned is ever rounded away.
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

    fun reset() {
        level = 1
        experience = 0
        totalExperience = 0
    }

    /** What it costs to leave [level] for the next one. Each level is [levelStep] dearer. */
    private fun costOfLevel(level: Int): Int = firstLevelCost + (level - 1) * levelStep

    companion object {
        /**
         * What killing an enemy from [waveIndex] is worth.
         *
         * Scaled by the wave so that pressing on pays better than farming the opening minute,
         * which would otherwise be the safest way to level: early enemies die to one shot.
         */
        fun experienceForKill(waveIndex: Int): Int =
            XP_PER_KILL + waveIndex * XP_PER_KILL_PER_WAVE
    }
}
