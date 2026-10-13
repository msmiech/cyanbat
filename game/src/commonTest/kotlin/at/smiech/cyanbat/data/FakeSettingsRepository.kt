package at.smiech.cyanbat.data

import at.smiech.cyanbat.Difficulty
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
    val window = MutableStateFlow(WindowMode.DEFAULT)
    val theme = MutableStateFlow(ThemeMode.DEFAULT)
    val chosenLanguage = MutableStateFlow(AppLanguage.DEFAULT)
    val lastDifficulty = MutableStateFlow(Difficulty.DEFAULT)
    val steering = MutableStateFlow<TouchSteering?>(null)
    val sensitivity = MutableStateFlow(StickSensitivity.DEFAULT)

    override val isMusicEnabled = music
    override val isSoundEnabled = sound
    override val isVibrationEnabled = vibration
    override val displayMode = display
    override val windowMode = window
    override val themeMode = theme
    override val language = chosenLanguage
    override val difficulty = lastDifficulty
    override val touchSteering = steering
    override val stickSensitivity = sensitivity

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

    override suspend fun setWindowMode(mode: WindowMode) {
        window.value = mode
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }

    override suspend fun setLanguage(language: AppLanguage) {
        putIntoEffect(language)
        chosenLanguage.value = language
    }

    override suspend fun setDifficulty(difficulty: Difficulty) {
        lastDifficulty.value = difficulty
    }

    override suspend fun setTouchSteering(steering: TouchSteering) {
        this.steering.value = steering
    }

    override suspend fun setStickSensitivity(sensitivity: StickSensitivity) {
        this.sensitivity.value = sensitivity
    }
}
