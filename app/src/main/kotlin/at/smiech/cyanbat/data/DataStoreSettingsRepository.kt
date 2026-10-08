package at.smiech.cyanbat.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import at.smiech.cyanbat.PREFS_KEY_DISPLAY_MODE
import at.smiech.cyanbat.PREFS_KEY_MUSIC
import at.smiech.cyanbat.PREFS_KEY_SOUNDS
import at.smiech.cyanbat.PREFS_KEY_THEME_MODE
import at.smiech.cyanbat.PREFS_KEY_VIBRATION
import at.smiech.engine.DisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] backed by Jetpack DataStore - all but the language, which from Android 13 is
 * the system's to keep; see [AppLocale].
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val isMusicEnabled: Flow<Boolean> =
        dataStore.data.map { it[PREFS_KEY_MUSIC] ?: true }

    override val isSoundEnabled: Flow<Boolean> =
        dataStore.data.map { it[PREFS_KEY_SOUNDS] ?: true }

    override val isVibrationEnabled: Flow<Boolean> =
        dataStore.data.map { it[PREFS_KEY_VIBRATION] ?: true }

    override val displayMode: Flow<DisplayMode> =
        dataStore.data.map { DisplayMode.fromName(it[PREFS_KEY_DISPLAY_MODE]) }

    // The game fills a phone's screen whatever is set, so there is nothing to keep; Settings does
    // not offer it here (windowModes).
    override val windowMode: Flow<WindowMode> = flowOf(WindowMode.FULLSCREEN)

    override val themeMode: Flow<ThemeMode> =
        dataStore.data.map { ThemeMode.fromName(it[PREFS_KEY_THEME_MODE]) }

    override val language: Flow<AppLanguage> = AppLocale.language

    override suspend fun setMusicEnabled(enabled: Boolean) {
        dataStore.edit { it[PREFS_KEY_MUSIC] = enabled }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[PREFS_KEY_SOUNDS] = enabled }
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[PREFS_KEY_VIBRATION] = enabled }
    }

    override suspend fun setDisplayMode(mode: DisplayMode) {
        dataStore.edit { it[PREFS_KEY_DISPLAY_MODE] = mode.name }
    }

    override suspend fun setWindowMode(mode: WindowMode) = Unit

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[PREFS_KEY_THEME_MODE] = mode.name }
    }

    override suspend fun setLanguage(language: AppLanguage) = AppLocale.set(language)
}
