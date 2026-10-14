package at.smiech.cyanbat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.smiech.cyanbat.data.AppLanguage
import at.smiech.cyanbat.data.SettingsRepository
import at.smiech.cyanbat.data.ThemeMode
import at.smiech.cyanbat.data.StickSensitivity
import at.smiech.cyanbat.data.TouchSteering
import at.smiech.cyanbat.data.WindowMode
import at.smiech.engine.DisplayMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    val isMusicEnabled: StateFlow<Boolean> = settings.isMusicEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

    val isSoundEnabled: StateFlow<Boolean> = settings.isSoundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

    val isVibrationEnabled: StateFlow<Boolean> = settings.isVibrationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

    val displayMode: StateFlow<DisplayMode> = settings.displayMode
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            DisplayMode.DEFAULT
        )

    val windowMode: StateFlow<WindowMode> = settings.windowMode
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            WindowMode.DEFAULT
        )

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            ThemeMode.DEFAULT
        )

    val language: StateFlow<AppLanguage> = settings.language
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            AppLanguage.DEFAULT
        )

    /** How a finger steers, shown as the default until the player has chosen. */
    val touchSteering: StateFlow<TouchSteering> = settings.touchSteering
        .map { it ?: TouchSteering.DEFAULT }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            TouchSteering.DEFAULT
        )

    val stickSensitivity: StateFlow<StickSensitivity> = settings.stickSensitivity
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            StickSensitivity.DEFAULT
        )

    fun setMusicEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setMusicEnabled(enabled)
    }

    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setSoundEnabled(enabled)
    }

    fun setVibrationEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setVibrationEnabled(enabled)
    }

    fun setDisplayMode(mode: DisplayMode) = viewModelScope.launch {
        settings.setDisplayMode(mode)
    }

    fun setWindowMode(mode: WindowMode) = viewModelScope.launch {
        settings.setWindowMode(mode)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settings.setThemeMode(mode)
    }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        settings.setLanguage(language)
    }

    fun setTouchSteering(steering: TouchSteering) = viewModelScope.launch {
        settings.setTouchSteering(steering)
    }

    fun setStickSensitivity(sensitivity: StickSensitivity) = viewModelScope.launch {
        settings.setStickSensitivity(sensitivity)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
