package at.smiech.cyanbat.ui

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.smiech.cyanbat.PREFS_KEY_MUSIC
import at.smiech.cyanbat.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    fun isMusicEnabled() =
        getApplication<Application>().dataStore.data.map { it[PREFS_KEY_MUSIC] }

    fun setMusicEnabled(enabled: Boolean) = viewModelScope.launch {
        getApplication<Application>().dataStore.edit { it[PREFS_KEY_MUSIC] = enabled }
    }
}