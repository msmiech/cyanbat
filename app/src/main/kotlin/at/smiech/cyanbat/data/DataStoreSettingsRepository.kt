package at.smiech.cyanbat.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import at.smiech.cyanbat.PREFS_KEY_MUSIC
import at.smiech.cyanbat.PREFS_KEY_SOUNDS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [SettingsRepository] backed by Jetpack DataStore. */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val isMusicEnabled: Flow<Boolean> =
        dataStore.data.map { it[PREFS_KEY_MUSIC] ?: true }

    override val isSoundEnabled: Flow<Boolean> =
        dataStore.data.map { it[PREFS_KEY_SOUNDS] ?: true }

    override suspend fun setMusicEnabled(enabled: Boolean) {
        dataStore.edit { it[PREFS_KEY_MUSIC] = enabled }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[PREFS_KEY_SOUNDS] = enabled }
    }
}
