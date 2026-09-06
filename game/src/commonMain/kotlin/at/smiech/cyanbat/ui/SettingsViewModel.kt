package at.smiech.cyanbat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.smiech.cyanbat.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    val isMusicEnabled: StateFlow<Boolean> = settings.isMusicEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

    val isSoundEnabled: StateFlow<Boolean> = settings.isSoundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

    fun setMusicEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setMusicEnabled(enabled)
    }

    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setSoundEnabled(enabled)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
