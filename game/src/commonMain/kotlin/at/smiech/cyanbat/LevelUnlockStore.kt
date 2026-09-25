package at.smiech.cyanbat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which levels the player has earned the right to start from.
 *
 * One number rather than a set, because levels unlock in order: clearing level N opens level N+1,
 * and everything below the highest is already open. Level 1 is always open.
 *
 * The same asymmetry as [HighscoreStore], for the same reason. The write happens as the boss dies,
 * moments before the player may tap through to the next screen and tear this one down, so it must
 * not be cancellable by that teardown - implementations own a scope for [unlockAsync]. And a write
 * only ever raises the stored value, so replaying level 1 cannot lock level 2 again.
 */
interface LevelUnlockStore {
    /** The highest level id the player may start from; 1 until level 1 has been cleared. */
    val highestUnlocked: Flow<Int>

    /** Opens every level up to and including [levelId]. Never closes one. */
    fun unlockAsync(levelId: Int)

    /** Remembers only for as long as the process lives. For tests, and for hosts with no storage. */
    class InMemory(initial: Int = 1) : LevelUnlockStore {
        private val highest = MutableStateFlow(initial)
        override val highestUnlocked: Flow<Int> = highest.asStateFlow()
        override fun unlockAsync(levelId: Int) {
            highest.value = maxOf(highest.value, levelId)
        }
    }
}
