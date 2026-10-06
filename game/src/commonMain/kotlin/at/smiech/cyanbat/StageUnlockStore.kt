package at.smiech.cyanbat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which stages the player may start from.
 *
 * One number rather than a set, because stages unlock in order: clearing stage N opens stage N+1.
 * Stage 1 is always open.
 *
 * As with [HighscoreStore], the write happens as the boss dies, just before the screen may be torn
 * down, so implementations own a scope for [unlockAsync]. A write only ever raises the stored
 * value, so replaying stage 1 cannot lock stage 2 again.
 */
interface StageUnlockStore {
    /** The highest stage id the player may start from; 1 until stage 1 has been cleared. */
    val highestUnlocked: Flow<Int>

    /** Opens every stage up to and including [stageId]. Never closes one. */
    fun unlockAsync(stageId: Int)

    /** Keeps the value in memory only, for tests and hosts with no storage. */
    class InMemory(initial: Int = 1) : StageUnlockStore {
        private val highest = MutableStateFlow(initial)
        override val highestUnlocked: Flow<Int> = highest.asStateFlow()
        override fun unlockAsync(stageId: Int) {
            highest.value = maxOf(highest.value, stageId)
        }
    }
}
