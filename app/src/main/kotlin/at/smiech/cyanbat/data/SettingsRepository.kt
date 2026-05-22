package at.smiech.cyanbat.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import at.smiech.cyanbat.PREFS_KEY_MUSIC
import at.smiech.cyanbat.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for accessing and modifying user settings.
 */
class SettingsRepository(private val context: Context) {

    val isMusicEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[PREFS_KEY_MUSIC] ?: true }

    suspend fun setMusicEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PREFS_KEY_MUSIC] = enabled }
    }
}
