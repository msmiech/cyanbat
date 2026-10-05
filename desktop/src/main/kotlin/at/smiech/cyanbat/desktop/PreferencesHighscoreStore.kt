package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.HighscoreStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * [HighscoreStore] on top of the JDK preferences API, which resolves to the registry on Windows
 * and a dotfile on macOS/Linux - no path handling or directory creation of our own. One key per
 * stage, the same names the Android store uses.
 *
 * Writes are synchronous but sub-millisecond, so unlike the Android store this needs no scope of
 * its own to outlive the screen being disposed. The flow is an in-memory mirror, as in
 * [PreferencesStageUnlockStore], so one instance has to be shared between the menu and the game.
 *
 * @param prefs where the scores live; the game's own node unless a test says otherwise.
 */
class PreferencesHighscoreStore(
    private val prefs: Preferences = Preferences.userRoot().node("at/smiech/cyanbat"),
) : HighscoreStore {

    init {
        // In the order they were needed: the single legacy score lands on stage 1, the cave it was
        // earned in, and the cave's scores then move to where the cave went.
        migrateLegacyHighscore()
        migrateStageOrder()
    }

    private val scores = MutableStateFlow(readAll())

    override val byStage: Flow<Map<Int, Int>> = scores.asStateFlow()

    override fun saveAsync(stageId: Int, value: Int) {
        val key = keyFor(stageId)
        val raised = maxOf(prefs.getInt(key, 0), value)
        prefs.putInt(key, raised)
        scores.value += stageId to raised
    }

    /** Every stage with a score stored, read back off the key names. */
    private fun readAll(): Map<Int, Int> = prefs.keys()
        .filter { it.startsWith(KEY_PREFIX) }
        .mapNotNull { key ->
            key.removePrefix(KEY_PREFIX).toIntOrNull()?.let { it to prefs.getInt(key, 0) }
        }
        .toMap()

    /**
     * Moves the single highscore kept before they were per stage onto stage 1. Every release that
     * kept one had the cave as its only stage, so that is where it was earned.
     */
    private fun migrateLegacyHighscore() {
        val legacy = prefs.get(LEGACY_KEY, null)?.toIntOrNull() ?: return
        val stageOne = keyFor(1)
        prefs.putInt(stageOne, maxOf(prefs.getInt(stageOne, 0), legacy))
        prefs.remove(LEGACY_KEY)
    }

    /**
     * Moves the highscores of the first two stages to where those stages went when they swapped
     * places: the jungle, which was stage 2 as the forest, flies first now, and the cave second. Done
     * once and marked done, since a swap run twice would undo itself; a store with nothing in it is
     * marked all the same, so the scores of the stages as they are now are never moved.
     */
    private fun migrateStageOrder() {
        if (prefs.getInt(ORDER_KEY, 0) >= JUNGLE_FIRST) return
        val first = keyFor(1)
        val second = keyFor(2)
        val cave = prefs.get(first, null)
        val forest = prefs.get(second, null)
        prefs.remove(first)
        prefs.remove(second)
        forest?.let { prefs.put(first, it) }
        cave?.let { prefs.put(second, it) }
        prefs.putInt(ORDER_KEY, JUNGLE_FIRST)
    }

    private companion object {
        const val KEY_PREFIX = "highscore_stage_"
        const val LEGACY_KEY = "highscore"

        /** Which order the stages' highscores are stored in, the same name the Android store uses. */
        const val ORDER_KEY = "stage_order"

        /** The order with the jungle first. Each later reordering would get its own number. */
        const val JUNGLE_FIRST = 1

        fun keyFor(stageId: Int) = KEY_PREFIX + stageId
    }
}
