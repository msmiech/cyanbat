package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.StageUnlockStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * [StageUnlockStore] on the JDK preferences API, next to the highscore.
 *
 * The flow is an in-memory mirror, as in [PreferencesSettingsRepository]: this process is the only
 * writer. One instance has to be shared between the menu and the game for that to hold, which is
 * why the desktop shell creates it once and hands it to both.
 */
class PreferencesStageUnlockStore : StageUnlockStore {
    private val prefs: Preferences = Preferences.userRoot().node("at/smiech/cyanbat")

    private val highest = MutableStateFlow(prefs.getInt(KEY, 1).coerceAtLeast(1))

    override val highestUnlocked: Flow<Int> = highest.asStateFlow()

    override fun unlockAsync(stageId: Int) {
        val raised = maxOf(prefs.getInt(KEY, 1), stageId)
        prefs.putInt(KEY, raised)
        highest.value = raised
    }

    private companion object {
        const val KEY = "highest_stage_unlocked"
    }
}
