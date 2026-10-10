package at.smiech.cyanbat.web

import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.AppLanguage
import at.smiech.cyanbat.data.SettingsRepository
import at.smiech.cyanbat.data.ThemeMode
import at.smiech.cyanbat.data.WindowMode
import at.smiech.engine.DisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * The browser's counterparts of the desktop's preferences stores, on the page's storage and under
 * the same key names. Each flow is an in-memory mirror, as on the desktop: the page is the only
 * writer, so one instance of each has to be shared between the menu and every run.
 *
 * Nothing is migrated: the browser came after the stages last moved, so whatever it has stored is
 * in today's order.
 */

/**
 * [SettingsRepository] on the page's storage. Making one puts the stored language into effect
 * ([WebLocale]), and the page makes its one before the menu reads a string.
 *
 * The window mode is kept like the rest, although Settings does not offer it here: the page fills
 * the browser's window, and the browser's own full screen takes it from there.
 */
internal class WebSettingsRepository(private val storage: WebStorage) : SettingsRepository {
    private val music = MutableStateFlow(storage.getBoolean(KEY_MUSIC, true))
    private val sound = MutableStateFlow(storage.getBoolean(KEY_SOUND, true))
    private val vibration = MutableStateFlow(storage.getBoolean(KEY_VIBRATION, true))
    private val display = MutableStateFlow(DisplayMode.fromName(storage.get(KEY_DISPLAY_MODE)))
    private val window = MutableStateFlow(WindowMode.fromName(storage.get(KEY_WINDOW_MODE)))
    private val theme = MutableStateFlow(ThemeMode.fromName(storage.get(KEY_THEME_MODE)))
    private val chosenLanguage = MutableStateFlow(AppLanguage.fromName(storage.get(KEY_LANGUAGE)))

    override val isMusicEnabled: Flow<Boolean> = music.asStateFlow()
    override val isSoundEnabled: Flow<Boolean> = sound.asStateFlow()
    override val isVibrationEnabled: Flow<Boolean> = vibration.asStateFlow()
    override val displayMode: Flow<DisplayMode> = display.asStateFlow()
    override val windowMode: Flow<WindowMode> = window.asStateFlow()
    override val themeMode: Flow<ThemeMode> = theme.asStateFlow()
    override val language: Flow<AppLanguage> = chosenLanguage.asStateFlow()

    init {
        WebLocale.apply(chosenLanguage.value)
    }

    override suspend fun setMusicEnabled(enabled: Boolean) {
        storage.put(KEY_MUSIC, enabled)
        music.value = enabled
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        storage.put(KEY_SOUND, enabled)
        sound.value = enabled
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        storage.put(KEY_VIBRATION, enabled)
        vibration.value = enabled
    }

    override suspend fun setDisplayMode(mode: DisplayMode) {
        storage.put(KEY_DISPLAY_MODE, mode.name)
        display.value = mode
    }

    override suspend fun setWindowMode(mode: WindowMode) {
        storage.put(KEY_WINDOW_MODE, mode.name)
        window.value = mode
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        storage.put(KEY_THEME_MODE, mode.name)
        theme.value = mode
    }

    override suspend fun setLanguage(language: AppLanguage) {
        storage.put(KEY_LANGUAGE, language.name)
        // In effect before the menu hears of it, so it redraws in it.
        WebLocale.apply(language)
        chosenLanguage.value = language
    }

    private companion object {
        const val KEY_MUSIC = "music_enabled"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_DISPLAY_MODE = "display_mode"
        const val KEY_WINDOW_MODE = "window_mode"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LANGUAGE = "language"
    }
}

/** [HighscoreStore] on the page's storage, one key per stage. */
internal class WebHighscoreStore(private val storage: WebStorage) : HighscoreStore {
    /** Every stage's score, mirrored from the store, read back off the key names. */
    private val scores = MutableStateFlow(
        storage.keys
            .filter { it.startsWith(KEY_PREFIX) }
            .mapNotNull { key ->
                key.removePrefix(KEY_PREFIX).toIntOrNull()?.let { it to storage.getInt(key, 0) }
            }
            .toMap()
    )

    override val byStage: Flow<Map<Int, Int>> = scores.asStateFlow()

    override fun saveAsync(stageId: Int, value: Int) {
        val key = KEY_PREFIX + stageId
        val raised = maxOf(storage.getInt(key, 0), value)
        storage.put(key, raised)
        scores.value += stageId to raised
    }

    private companion object {
        /** A stage's score is kept under this and its id. */
        const val KEY_PREFIX = "highscore_stage_"
    }
}

/** [StageUnlockStore] on the page's storage, next to the highscores. */
internal class WebStageUnlockStore(private val storage: WebStorage) : StageUnlockStore {
    /** The highest stage open, mirrored from the store. */
    private val highest = MutableStateFlow(storage.getInt(KEY, 1).coerceAtLeast(1))

    override val highestUnlocked: Flow<Int> = highest.asStateFlow()

    override fun unlockAsync(stageId: Int) {
        val raised = maxOf(storage.getInt(KEY, 1), stageId)
        storage.put(KEY, raised)
        highest.value = raised
    }

    private companion object {
        const val KEY = "highest_stage_unlocked"
    }
}
