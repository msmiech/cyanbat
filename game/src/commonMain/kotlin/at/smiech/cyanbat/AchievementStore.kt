package at.smiech.cyanbat

import at.smiech.engine.FrameCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which [Achievement]s the player has earned, each with a picture of the moment it was earned.
 *
 * An achievement is earned once and kept: unlocking one again changes nothing, its first picture
 * included. As with [HighscoreStore], the unlock happens in the middle of a run that may be torn
 * down a moment later, and turning the picture into a file takes a while, so implementations own a
 * scope for [unlockAsync] that outlives the run.
 *
 * Achievements earned together share one picture, and a store keeps it once.
 */
interface AchievementStore {
    /** Every achievement earned so far. */
    val unlocked: Flow<Set<Achievement>>

    /**
     * Earns each of [achievements] not yet earned, with [screenshot] as the picture of the moment,
     * or none where there is nothing to show: one earned before there were achievements, which a
     * stage's clear stands for.
     */
    fun unlockAsync(achievements: Collection<Achievement>, screenshot: FrameCapture?)

    /** The picture [achievement] was earned with, as a PNG file; null for none, or none yet. */
    suspend fun screenshot(achievement: Achievement): ByteArray?

    /**
     * Keeps everything in memory only, for tests and hosts with no storage. A picture is only made
     * into a file when it is asked for, which most tests never do.
     */
    class InMemory(initial: Set<Achievement> = emptySet()) : AchievementStore {
        private val earned = MutableStateFlow(initial)
        private val pictures = HashMap<Achievement, FrameCapture>()

        override val unlocked: Flow<Set<Achievement>> = earned.asStateFlow()

        override fun unlockAsync(achievements: Collection<Achievement>, screenshot: FrameCapture?) {
            val new = achievements.toSet() - earned.value
            if (new.isEmpty()) return
            if (screenshot != null) new.forEach { pictures[it] = screenshot }
            earned.value += new
        }

        override suspend fun screenshot(achievement: Achievement): ByteArray? =
            pictures[achievement]?.encodePng()
    }
}

/**
 * The achievements a player who cleared stages before there were achievements has already earned:
 * what each stage's best clear in [clears] would have earned then. How it was flown is not known,
 * so it earns nothing for that, and there is no picture of the moment.
 */
fun achievementsOfClears(clears: Map<Int, Difficulty>): Set<Achievement> =
    clears.flatMap { (stageId, difficulty) -> Achievement.forClear(stageId, difficulty) }.toSet()
