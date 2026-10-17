package at.smiech.cyanbat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementTest {

    @Test
    fun everyAchievementIsStoredUnderAKeyOfItsOwn() {
        val keys = Achievement.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        for (achievement in Achievement.entries) {
            assertEquals(achievement, Achievement.ofKey(achievement.key))
        }
        assertEquals(null, Achievement.ofKey("an achievement from a later version"))
    }

    @Test
    fun aLevelEarnsEveryMilestoneUpToIt() {
        assertEquals(emptyList(), Achievement.forLevel(9))
        assertEquals(listOf(Achievement.LEVEL_10), Achievement.forLevel(10))
        assertEquals(
            listOf(Achievement.LEVEL_10, Achievement.LEVEL_20, Achievement.LEVEL_30),
            Achievement.forLevel(39),
        )
        assertEquals(
            listOf(
                Achievement.LEVEL_10,
                Achievement.LEVEL_20,
                Achievement.LEVEL_30,
                Achievement.LEVEL_40,
                Achievement.LEVEL_50,
            ),
            Achievement.forLevel(73),
        )
    }

    @Test
    fun theJungleBeatenOnHardEarnsItsEasierClearsToo() {
        assertEquals(
            listOf(Achievement.STAGE_1_EASY),
            Achievement.forClear(1, Difficulty.EASY),
        )
        assertEquals(
            listOf(Achievement.STAGE_1_EASY, Achievement.STAGE_1_NORMAL),
            Achievement.forClear(1, Difficulty.NORMAL),
        )
        assertEquals(
            listOf(Achievement.STAGE_1_EASY, Achievement.STAGE_1_NORMAL, Achievement.STAGE_1_HARD),
            Achievement.forClear(1, Difficulty.HARD),
        )
    }

    @Test
    fun theOtherStagesEarnTheirClearOnAnyDifficulty() {
        for (difficulty in Difficulty.entries) {
            assertEquals(listOf(Achievement.STAGE_2), Achievement.forClear(2, difficulty))
            assertEquals(listOf(Achievement.STAGE_3), Achievement.forClear(3, difficulty))
            assertEquals(listOf(Achievement.STAGE_4), Achievement.forClear(4, difficulty))
        }
    }

    @Test
    fun aStageFlownCleanEarnsWhatItWasFlownFor() {
        val clean = Achievement.forClear(3, Difficulty.EASY, untouched = true, nothingEscaped = true)
        assertEquals(
            listOf(Achievement.STAGE_3, Achievement.UNTOUCHABLE, Achievement.NOTHING_GETS_PAST),
            clean,
        )
        assertEquals(
            listOf(Achievement.STAGE_2, Achievement.NOTHING_GETS_PAST),
            Achievement.forClear(2, Difficulty.HARD, untouched = false, nothingEscaped = true),
        )
    }

    @Test
    fun clearsFromBeforeAchievementsEarnTheirStagesAndNothingElse() {
        val owed = achievementsOfClears(mapOf(1 to Difficulty.NORMAL, 4 to Difficulty.EASY))
        assertEquals(
            setOf(Achievement.STAGE_1_EASY, Achievement.STAGE_1_NORMAL, Achievement.STAGE_4),
            owed,
        )
        assertTrue(achievementsOfClears(emptyMap()).isEmpty())
    }
}
