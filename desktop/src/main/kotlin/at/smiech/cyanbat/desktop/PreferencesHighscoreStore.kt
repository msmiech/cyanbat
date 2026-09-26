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
        migrateLegacyHighscore()
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
        .mapNotNull { key -> key.removePrefix(KEY_PREFIX).toIntOrNull()?.let { it to prefs.getInt(key, 0) } }
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

    private companion object {
        const val KEY_PREFIX = "highscore_stage_"
        const val LEGACY_KEY = "highscore"

        fun keyFor(stageId: Int) = KEY_PREFIX + stageId
    }
}
