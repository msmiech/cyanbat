package at.smiech.cyanbat

import at.smiech.cyanbat.progress.PowerUp
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.difficulty_easy
import at.smiech.cyanbat.resources.difficulty_easy_cleared
import at.smiech.cyanbat.resources.difficulty_easy_description
import at.smiech.cyanbat.resources.difficulty_hard
import at.smiech.cyanbat.resources.difficulty_hard_cleared
import at.smiech.cyanbat.resources.difficulty_hard_description
import at.smiech.cyanbat.resources.difficulty_normal
import at.smiech.cyanbat.resources.difficulty_normal_cleared
import at.smiech.cyanbat.resources.difficulty_normal_description
import org.jetbrains.compose.resources.StringResource

/**
 * How hard a run is, chosen as each stage starts.
 *
 * [HARD] is the game as it is tuned: every other difficulty is a discount on it, so every factor
 * here is 1 there, it hands out no power-ups, and the stages, the waves and the bosses are designed
 * against it alone. An easier one sends fewer enemies and makes a stronger bat of the one it starts
 * with, holding a power-up or two; the enemies themselves are as tough as ever, and so are the
 * bosses.
 *
 * In order from easiest to hardest, which is also the order of their [stars]: a stage's record
 * keeps the hardest difficulty it has been cleared on.
 *
 * @param stars what clearing a stage on it is worth on the stage select, one to three.
 * @param enemyDensity how many enemies arrive, against Hard's: the gaps between arrivals are
 *   stretched by its inverse. The waves send the same species at the same strength, only fewer.
 * @param experienceFactor what every kill is worth in experience, against Hard's.
 * @param healthFactor the size of the bat's health bar at the start of a run, against Hard's.
 * @param damageFactor what the bat's shot deals at the start of a run, against Hard's. Its orbs and
 *   its charged wake deal a share of it, so they hit harder too.
 * @param startingPowerUps what the bat holds before its first level up, each taken as a pick is. The
 *   offers go on as they would after those picks: a power-up that stacks can be picked again.
 */
enum class Difficulty(
    val stars: Int,
    val title: StringResource,
    val description: StringResource,
    /** What the stage select says of a stage whose best clear was on this. */
    val cleared: StringResource,
    val enemyDensity: Float,
    val experienceFactor: Float,
    val healthFactor: Float,
    val damageFactor: Float,
    val startingPowerUps: List<PowerUp>,
) {
    EASY(
        stars = 1,
        title = Res.string.difficulty_easy,
        description = Res.string.difficulty_easy_description,
        cleared = Res.string.difficulty_easy_cleared,
        enemyDensity = 0.7f,
        experienceFactor = 1.5f,
        // Five of the jungle's last wave's hits, which finish the bat in three on Hard.
        healthFactor = 1.5f,
        damageFactor = 1.3f,
        startingPowerUps = listOf(PowerUp.SECOND_LIFE, PowerUp.REGENERATION),
    ),
    NORMAL(
        stars = 2,
        title = Res.string.difficulty_normal,
        description = Res.string.difficulty_normal_description,
        cleared = Res.string.difficulty_normal_cleared,
        enemyDensity = 0.85f,
        experienceFactor = 1.25f,
        // Four of them rather than three.
        healthFactor = 1.25f,
        damageFactor = 1.15f,
        startingPowerUps = listOf(PowerUp.REGENERATION),
    ),
    HARD(
        stars = 3,
        title = Res.string.difficulty_hard,
        description = Res.string.difficulty_hard_description,
        cleared = Res.string.difficulty_hard_cleared,
        enemyDensity = 1f,
        experienceFactor = 1f,
        healthFactor = 1f,
        damageFactor = 1f,
        startingPowerUps = emptyList(),
    );

    companion object {
        /** Where the cursor starts the first time the player is asked: the middle way. */
        val DEFAULT = NORMAL

        /** The difficulty worth [stars], or null for a number none is worth. */
        fun ofStars(stars: Int): Difficulty? = entries.firstOrNull { it.stars == stars }

        /** The difficulty stored under [name], or [DEFAULT] for none or one no longer known. */
        fun fromName(name: String?): Difficulty = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
