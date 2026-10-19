package at.smiech.cyanbat

import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.achievement_first_fall
import at.smiech.cyanbat.resources.achievement_first_fall_description
import at.smiech.cyanbat.resources.achievement_level_10
import at.smiech.cyanbat.resources.achievement_level_10_description
import at.smiech.cyanbat.resources.achievement_level_20
import at.smiech.cyanbat.resources.achievement_level_20_description
import at.smiech.cyanbat.resources.achievement_level_30
import at.smiech.cyanbat.resources.achievement_level_30_description
import at.smiech.cyanbat.resources.achievement_level_40
import at.smiech.cyanbat.resources.achievement_level_40_description
import at.smiech.cyanbat.resources.achievement_level_50
import at.smiech.cyanbat.resources.achievement_level_50_description
import at.smiech.cyanbat.resources.achievement_maxed_out
import at.smiech.cyanbat.resources.achievement_maxed_out_description
import at.smiech.cyanbat.resources.achievement_nothing_gets_past
import at.smiech.cyanbat.resources.achievement_nothing_gets_past_description
import at.smiech.cyanbat.resources.achievement_stage_1_easy
import at.smiech.cyanbat.resources.achievement_stage_1_easy_description
import at.smiech.cyanbat.resources.achievement_stage_1_hard
import at.smiech.cyanbat.resources.achievement_stage_1_hard_description
import at.smiech.cyanbat.resources.achievement_stage_1_normal
import at.smiech.cyanbat.resources.achievement_stage_1_normal_description
import at.smiech.cyanbat.resources.achievement_stage_2
import at.smiech.cyanbat.resources.achievement_stage_2_description
import at.smiech.cyanbat.resources.achievement_stage_3
import at.smiech.cyanbat.resources.achievement_stage_3_description
import at.smiech.cyanbat.resources.achievement_stage_4
import at.smiech.cyanbat.resources.achievement_stage_4_description
import at.smiech.cyanbat.resources.achievement_untouchable
import at.smiech.cyanbat.resources.achievement_untouchable_description
import org.jetbrains.compose.resources.StringResource

/**
 * Something the player can do once and keep: a level reached, a stage beaten, a run flown a certain
 * way. Kept by an [AchievementStore], with a picture of the moment it was earned.
 *
 * The game's own, shown on its own screen, since most of the platforms it runs on have no
 * achievements of their own to report them to. Stored by [key], so reordering or renaming the
 * entries cannot hand one player's achievement to another.
 *
 * Listed in the order the achievements screen shows them.
 *
 * @param title what it is called.
 * @param description what earns it.
 */
enum class Achievement(
    val key: String,
    val title: StringResource,
    val description: StringResource,
) {
    LEVEL_10(
        "level_10",
        Res.string.achievement_level_10,
        Res.string.achievement_level_10_description
    ),
    LEVEL_20(
        "level_20",
        Res.string.achievement_level_20,
        Res.string.achievement_level_20_description
    ),
    LEVEL_30(
        "level_30",
        Res.string.achievement_level_30,
        Res.string.achievement_level_30_description
    ),
    LEVEL_40(
        "level_40",
        Res.string.achievement_level_40,
        Res.string.achievement_level_40_description
    ),
    LEVEL_50(
        "level_50",
        Res.string.achievement_level_50,
        Res.string.achievement_level_50_description
    ),
    STAGE_1_EASY(
        "stage_1_easy",
        Res.string.achievement_stage_1_easy,
        Res.string.achievement_stage_1_easy_description,
    ),
    STAGE_1_NORMAL(
        "stage_1_normal",
        Res.string.achievement_stage_1_normal,
        Res.string.achievement_stage_1_normal_description,
    ),
    STAGE_1_HARD(
        "stage_1_hard",
        Res.string.achievement_stage_1_hard,
        Res.string.achievement_stage_1_hard_description,
    ),
    STAGE_2("stage_2", Res.string.achievement_stage_2, Res.string.achievement_stage_2_description),
    STAGE_3("stage_3", Res.string.achievement_stage_3, Res.string.achievement_stage_3_description),
    STAGE_4("stage_4", Res.string.achievement_stage_4, Res.string.achievement_stage_4_description),
    UNTOUCHABLE(
        "untouchable",
        Res.string.achievement_untouchable,
        Res.string.achievement_untouchable_description,
    ),
    NOTHING_GETS_PAST(
        "nothing_gets_past",
        Res.string.achievement_nothing_gets_past,
        Res.string.achievement_nothing_gets_past_description,
    ),
    MAXED_OUT(
        "maxed_out",
        Res.string.achievement_maxed_out,
        Res.string.achievement_maxed_out_description,
    ),
    FIRST_FALL(
        "first_fall",
        Res.string.achievement_first_fall,
        Res.string.achievement_first_fall_description,
    );

    companion object {
        /** The levels that earn something, and what each earns. */
        private val LEVELS = listOf(
            10 to LEVEL_10,
            20 to LEVEL_20,
            30 to LEVEL_30,
            40 to LEVEL_40,
            50 to LEVEL_50,
        )

        /** The achievement stored under [key], or null for one this version does not know. */
        fun ofKey(key: String): Achievement? = entries.firstOrNull { it.key == key }

        /**
         * Everything the bat being at [level] has earned: every milestone up to it, so a run that
         * leaps past one in a single kill still earns it.
         */
        fun forLevel(level: Int): List<Achievement> =
            LEVELS.filter { (milestone, _) -> level >= milestone }.map { it.second }

        /**
         * Everything clearing stage [stageId] on [difficulty] earns. The jungle earns one per
         * difficulty, and a clear on a harder one earns the easier ones' too: beaten on Hard, it
         * would have been beaten on Easy. The other stages earn one on any difficulty.
         *
         * @param untouched whether the bat was never hit on the way.
         * @param nothingEscaped whether no enemy of the stage got away from it.
         */
        fun forClear(
            stageId: Int,
            difficulty: Difficulty,
            untouched: Boolean = false,
            nothingEscaped: Boolean = false,
        ): List<Achievement> = buildList {
            when (stageId) {
                1 -> {
                    add(STAGE_1_EASY)
                    if (difficulty >= Difficulty.NORMAL) add(STAGE_1_NORMAL)
                    if (difficulty >= Difficulty.HARD) add(STAGE_1_HARD)
                }

                2 -> add(STAGE_2)
                3 -> add(STAGE_3)
                4 -> add(STAGE_4)
            }
            if (untouched) add(UNTOUCHABLE)
            if (nothingEscaped) add(NOTHING_GETS_PAST)
        }
    }
}
