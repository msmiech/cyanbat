package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.data.AppLanguage
import at.smiech.cyanbat.data.SettingsRepository
import at.smiech.cyanbat.data.StickSensitivity
import at.smiech.cyanbat.data.ThemeMode
import at.smiech.cyanbat.data.TouchSteering
import at.smiech.cyanbat.data.WindowMode
import at.smiech.engine.DisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * [SettingsRepository] on the JDK preferences API - the registry on Windows, a dotfile
 * elsewhere. Avoids putting DataStore and okio on the desktop classpath for a handful of settings.
 *
 * The flows are in-memory mirrors: this process is the only writer, so they cannot go stale.
 *
 * Vibration is stored like the rest, and rumbles a game controller ([DesktopGamepads]). So is the
 * touch steering, which the desktop, flown by mouse, neither asks for nor offers.
 *
 * Making one puts the stored language into effect ([DesktopLocale]), and the desktop makes its one
 * before the menu reads a string.
 *
 * @param prefs where the settings live; the game's own node unless a test says otherwise.
 * @param windowModeUnlessSet how the game takes its screen for a player who has not said: on a
 *   Steam Deck in full screen ([isSteamDeck]), anywhere else in a window.
 */
class PreferencesSettingsRepository(
    private val prefs: Preferences = Preferences.userRoot().node("at/smiech/cyanbat"),
    windowModeUnlessSet: WindowMode =
        if (isSteamDeck()) WindowMode.FULLSCREEN else WindowMode.WINDOWED,
) : SettingsRepository {

    private val music = MutableStateFlow(prefs.getBoolean(KEY_MUSIC, true))
    private val sound = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    private val vibration = MutableStateFlow(prefs.getBoolean(KEY_VIBRATION, true))
    private val display = MutableStateFlow(DisplayMode.fromName(prefs.get(KEY_DISPLAY_MODE, null)))
    private val window = MutableStateFlow(
        prefs.get(KEY_WINDOW_MODE, null)?.let(WindowMode::fromName) ?: windowModeUnlessSet
    )
    private val theme = MutableStateFlow(ThemeMode.fromName(prefs.get(KEY_THEME_MODE, null)))
    private val chosenLanguage =
        MutableStateFlow(AppLanguage.fromName(prefs.get(KEY_LANGUAGE, null)))
    private val lastDifficulty =
        MutableStateFlow(Difficulty.fromName(prefs.get(KEY_DIFFICULTY, null)))
    private val steering =
        MutableStateFlow(TouchSteering.fromName(prefs.get(KEY_TOUCH_STEERING, null)))
    private val sensitivity =
        MutableStateFlow(StickSensitivity.fromName(prefs.get(KEY_STICK_SENSITIVITY, null)))

    override val isMusicEnabled: Flow<Boolean> = music.asStateFlow()
    override val isSoundEnabled: Flow<Boolean> = sound.asStateFlow()
    override val isVibrationEnabled: Flow<Boolean> = vibration.asStateFlow()
    override val displayMode: Flow<DisplayMode> = display.asStateFlow()

    /** A state, so the window can open the way it was left without waiting on the flow. */
    override val windowMode: StateFlow<WindowMode> = window.asStateFlow()

    /**
     * The full screen the player last chose, the system's or borderless, which the keys that toggle
     * full screen go back to; the system's until they choose.
     */
    val lastFullScreenMode: WindowMode
        get() = WindowMode.fromName(prefs.get(KEY_FULL_SCREEN_MODE, null))
            .takeUnless { it == WindowMode.WINDOWED } ?: WindowMode.FULLSCREEN

    override val themeMode: Flow<ThemeMode> = theme.asStateFlow()
    override val language: Flow<AppLanguage> = chosenLanguage.asStateFlow()
    override val difficulty: Flow<Difficulty> = lastDifficulty.asStateFlow()
    override val touchSteering: Flow<TouchSteering?> = steering.asStateFlow()
    override val stickSensitivity: Flow<StickSensitivity> = sensitivity.asStateFlow()

    init {
        DesktopLocale.apply(chosenLanguage.value)
    }

    override suspend fun setMusicEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_MUSIC, enabled)
        music.value = enabled
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_SOUND, enabled)
        sound.value = enabled
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_VIBRATION, enabled)
        vibration.value = enabled
    }

    override suspend fun setDisplayMode(mode: DisplayMode) {
        prefs.put(KEY_DISPLAY_MODE, mode.name)
        display.value = mode
    }

    override suspend fun setWindowMode(mode: WindowMode) {
        prefs.put(KEY_WINDOW_MODE, mode.name)
        if (mode != WindowMode.WINDOWED) prefs.put(KEY_FULL_SCREEN_MODE, mode.name)
        window.value = mode
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        prefs.put(KEY_THEME_MODE, mode.name)
        theme.value = mode
    }

    override suspend fun setLanguage(language: AppLanguage) {
        prefs.put(KEY_LANGUAGE, language.name)
        // In effect before the menu hears of it, so it redraws in it.
        DesktopLocale.apply(language)
        chosenLanguage.value = language
    }

    override suspend fun setDifficulty(difficulty: Difficulty) {
        prefs.put(KEY_DIFFICULTY, difficulty.name)
        lastDifficulty.value = difficulty
    }

    override suspend fun setTouchSteering(steering: TouchSteering) {
        prefs.put(KEY_TOUCH_STEERING, steering.name)
        this.steering.value = steering
    }

    override suspend fun setStickSensitivity(sensitivity: StickSensitivity) {
        prefs.put(KEY_STICK_SENSITIVITY, sensitivity.name)
        this.sensitivity.value = sensitivity
    }

    private companion object {
        const val KEY_MUSIC = "music_enabled"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_DISPLAY_MODE = "display_mode"
        const val KEY_WINDOW_MODE = "window_mode"
        const val KEY_FULL_SCREEN_MODE = "full_screen_mode"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LANGUAGE = "language"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_TOUCH_STEERING = "touch_steering"
        const val KEY_STICK_SENSITIVITY = "stick_sensitivity"
    }
}
