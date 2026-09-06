package at.smiech.cyanbat.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.PREFS_KEY_HIGH_SCORE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * [HighscoreStore] backed by Jetpack DataStore.
 *
 * Owns a process-lifetime scope so a save issued as the game screen is being disposed still
 * completes - exactly the runs that earn a highscore are the ones that would otherwise lose it.
 */
class DataStoreHighscoreStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : HighscoreStore {

    override suspend fun read(): Int = dataStore.data.first()[PREFS_KEY_HIGH_SCORE] ?: 0

    override fun saveAsync(value: Int) {
        scope.launch { dataStore.edit { it[PREFS_KEY_HIGH_SCORE] = value } }
    }
}
