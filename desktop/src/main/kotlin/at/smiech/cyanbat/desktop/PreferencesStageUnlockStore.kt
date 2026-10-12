package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.clearsBeforeDifficulties
import at.smiech.cyanbat.harderThanBest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * [StageUnlockStore] on the JDK preferences API, next to the highscore.
 *
 * The flows are in-memory mirrors, as in [PreferencesSettingsRepository]: this process is the only
 * writer. One instance has to be shared between the menu and the game for that to hold, which is
 * why the desktop shell creates it once and hands it to both.
 *
 * @param prefs the node to keep it in: the game's own, shared with the highscores and the settings,
 *   unless a test hands it one of its own.
 */
class PreferencesStageUnlockStore(
    private val prefs: Preferences = Preferences.userRoot().node("at/smiech/cyanbat"),
) : StageUnlockStore {

    /** The highest stage open, mirrored from the store. */
    private val highest = MutableStateFlow(prefs.getInt(KEY, 1).coerceAtLeast(1))

    init {
        keepClearsBeforeDifficulties()
    }

    /** Every stage's best clear, mirrored from the store, read back off the key names. */
    private val cleared = MutableStateFlow(
        prefs.keys()
            .filter { it.startsWith(CLEARED_PREFIX) }
            .mapNotNull { key ->
                val stageId = key.removePrefix(CLEARED_PREFIX).toIntOrNull()
                val difficulty = Difficulty.ofStars(prefs.getInt(key, 0))
                if (stageId != null && difficulty != null) stageId to difficulty else null
            }
            .toMap()
    )

    override val highestUnlocked: Flow<Int> = highest.asStateFlow()

    override val clears: Flow<Map<Int, Difficulty>> = cleared.asStateFlow()

    override fun unlockAsync(stageId: Int) {
        val raised = maxOf(prefs.getInt(KEY, 1), stageId)
        prefs.putInt(KEY, raised)
        highest.value = raised
    }

    override fun recordClearAsync(stageId: Int, difficulty: Difficulty) {
        if (!raiseClear(stageId, difficulty)) return
        cleared.value += stageId to difficulty
    }

    /** Stores [difficulty] as [stageId]'s best clear if it beats it, and says whether it did. */
    private fun raiseClear(stageId: Int, difficulty: Difficulty): Boolean {
        val key = CLEARED_PREFIX + stageId
        if (!harderThanBest(Difficulty.ofStars(prefs.getInt(key, 0)), difficulty)) return false
        prefs.putInt(key, difficulty.stars)
        return true
    }

    /**
     * Gives a store kept before there were difficulties the clears its unlocks stand for, once; see
     * [clearsBeforeDifficulties]. Done again, it would read a stage opened by a clear on Easy as one
     * cleared on Hard, so it is marked done, on a store with nothing in it as well.
     */
    private fun keepClearsBeforeDifficulties() {
        if (prefs.getBoolean(CLEARS_KEPT, false)) return
        for ((stageId, difficulty) in clearsBeforeDifficulties(highest.value)) {
            raiseClear(stageId, difficulty)
        }
        prefs.putBoolean(CLEARS_KEPT, true)
    }

    private companion object {
        /** The highest stage open, under the name the Android store uses. */
        const val KEY = "highest_stage_unlocked"

        /** A stage's best clear, as its difficulty's stars, under this and its id, as on Android. */
        const val CLEARED_PREFIX = "cleared_stage_"

        /** Whether the clears from before difficulties are in, as on Android. */
        const val CLEARS_KEPT = "stage_clears_kept"
    }
}
