package at.smiech.cyanbat.data

import at.smiech.engine.DisplayMode
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [SettingsRepository] held in memory, whose values a test sets directly.
 *
 * @param putIntoEffect what a host does with a language chosen, before the menu hears of it.
 */
internal class FakeSettingsRepository(
    private val putIntoEffect: (AppLanguage) -> Unit = {},
) : SettingsRepository {
    val music = MutableStateFlow(true)
    val sound = MutableStateFlow(true)
    val vibration = MutableStateFlow(true)
    val display = MutableStateFlow(DisplayMode.DEFAULT)
    val theme = MutableStateFlow(ThemeMode.DEFAULT)
    val chosenLanguage = MutableStateFlow(AppLanguage.DEFAULT)

    override val isMusicEnabled = music
    override val isSoundEnabled = sound
    override val isVibrationEnabled = vibration
    override val displayMode = display
    override val themeMode = theme
    override val language = chosenLanguage

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

    override suspend fun setLanguage(language: AppLanguage) {
        putIntoEffect(language)
        chosenLanguage.value = language
    }
}
