package at.smiech.cyanbat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which stages the player has earned the right to start from.
 *
 * One number rather than a set, because stages unlock in order: clearing stage N opens stage N+1,
 * and everything below the highest is already open. Stage 1 is always open.
 *
 * The same asymmetry as [HighscoreStore], for the same reason. The write happens as the boss dies,
 * moments before the player may tap through to the next screen and tear this one down, so it must
 * not be cancellable by that teardown - implementations own a scope for [unlockAsync]. And a write
 * only ever raises the stored value, so replaying stage 1 cannot lock stage 2 again.
 */
interface StageUnlockStore {
    /** The highest stage id the player may start from; 1 until stage 1 has been cleared. */
    val highestUnlocked: Flow<Int>

    /** Opens every stage up to and including [stageId]. Never closes one. */
    fun unlockAsync(stageId: Int)

    /** Remembers only for as long as the process lives. For tests, and for hosts with no storage. */
    class InMemory(initial: Int = 1) : StageUnlockStore {
        private val highest = MutableStateFlow(initial)
        override val highestUnlocked: Flow<Int> = highest.asStateFlow()
        override fun unlockAsync(stageId: Int) {
            highest.value = maxOf(highest.value, stageId)
        }
    }
}
