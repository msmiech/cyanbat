package at.smiech.cyanbat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which stages the player may start from, and the hardest [Difficulty] each has been cleared on.
 *
 * The unlock is one number rather than a set, because stages unlock in order: clearing stage N
 * opens stage N+1. Stage 1 is always open.
 *
 * As with [HighscoreStore], the writes happen as the boss dies, just before the screen may be torn
 * down, so implementations own a scope for [unlockAsync] and [recordClearAsync]. A write only ever
 * raises the stored value, so replaying stage 1 cannot lock stage 2 again, and clearing it on Easy
 * cannot take away the stars of a clear on Hard.
 *
 * A store kept before there were difficulties has unlocks and no clears; it takes them once, from
 * [clearsBeforeDifficulties].
 */
interface StageUnlockStore {
    /** The highest stage id the player may start from; 1 until stage 1 has been cleared. */
    val highestUnlocked: Flow<Int>

    /** The hardest difficulty each stage has been cleared on, by stage id; absent if never. */
    val clears: Flow<Map<Int, Difficulty>>

    /** Opens every stage up to and including [stageId]. Never closes one. */
    fun unlockAsync(stageId: Int)

    /** Records [stageId] as cleared on [difficulty], if that is harder than its best. */
    fun recordClearAsync(stageId: Int, difficulty: Difficulty)

    /** Keeps everything in memory only, for tests and hosts with no storage. */
    class InMemory(
        initial: Int = 1,
        initialClears: Map<Int, Difficulty> = emptyMap(),
    ) : StageUnlockStore {
        private val highest = MutableStateFlow(initial)
        private val cleared = MutableStateFlow(initialClears)
        override val highestUnlocked: Flow<Int> = highest.asStateFlow()
        override val clears: Flow<Map<Int, Difficulty>> = cleared.asStateFlow()
        override fun unlockAsync(stageId: Int) {
            highest.value = maxOf(highest.value, stageId)
        }

        override fun recordClearAsync(stageId: Int, difficulty: Difficulty) {
            if (harderThanBest(cleared.value[stageId], difficulty)) {
                cleared.value += stageId to difficulty
            }
        }
    }
}

/** Whether a clear on [difficulty] beats a stage's [best], null for a stage never cleared. */
fun harderThanBest(best: Difficulty?, difficulty: Difficulty): Boolean =
    best == null || difficulty > best

/**
 * The clears a store kept before there were difficulties has to take on, from its
 * [highestUnlocked]: every stage before it, cleared on [Difficulty.HARD].
 *
 * Such a store kept a clear only as the next stage's unlock, and every stage was flown as Hard is
 * now. The last stage unlocks nothing, so a clear of it from then is not known.
 */
fun clearsBeforeDifficulties(highestUnlocked: Int): Map<Int, Difficulty> =
    (1 until highestUnlocked).associateWith { Difficulty.HARD }
