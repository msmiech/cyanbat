package at.smiech.cyanbat.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import at.smiech.cyanbat.LevelUnlockStore
import at.smiech.cyanbat.PREFS_KEY_HIGHEST_LEVEL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * [LevelUnlockStore] backed by Jetpack DataStore, in the same file as the highscore and settings.
 *
 * Owns a process-lifetime scope, like [DataStoreHighscoreStore], so an unlock issued as the boss
 * dies still lands if the player taps straight through to the next screen.
 */
class DataStoreLevelUnlockStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : LevelUnlockStore {

    override val highestUnlocked: Flow<Int> =
        dataStore.data.map { (it[PREFS_KEY_HIGHEST_LEVEL] ?: 1).coerceAtLeast(1) }

    override fun unlockAsync(levelId: Int) {
        scope.launch {
            dataStore.edit {
                it[PREFS_KEY_HIGHEST_LEVEL] = maxOf(it[PREFS_KEY_HIGHEST_LEVEL] ?: 1, levelId)
            }
        }
    }
}
