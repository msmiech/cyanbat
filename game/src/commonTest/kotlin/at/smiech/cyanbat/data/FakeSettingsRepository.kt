package at.smiech.cyanbat.data

import at.smiech.engine.DisplayMode
import kotlinx.coroutines.flow.MutableStateFlow

/** A [SettingsRepository] held in memory, whose values a test sets directly. */
internal class FakeSettingsRepository : SettingsRepository {
    val music = MutableStateFlow(true)
    val sound = MutableStateFlow(true)
    val vibration = MutableStateFlow(true)
    val display = MutableStateFlow(DisplayMode.DEFAULT)
    val theme = MutableStateFlow(ThemeMode.DEFAULT)

    override val isMusicEnabled = music
    override val isSoundEnabled = sound
    override val isVibrationEnabled = vibration
    override val displayMode = display
    override val themeMode = theme

    override suspend fun setMusicEnabled(enabled: Boolean) {
        music.value = enabled
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        sound.value = enabled
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        vibration.value = enabled
    }

    override suspend fun setDisplayMode(mode: DisplayMode) {
        display.value = mode
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }
}
