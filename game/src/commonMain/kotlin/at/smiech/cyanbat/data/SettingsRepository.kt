package at.smiech.cyanbat.data

import at.smiech.engine.DisplayMode
import kotlinx.coroutines.flow.Flow

/**
 * User settings, as the shared UI sees them.
 *
 * An interface rather than a concrete DataStore wrapper so desktop can back it with the JDK
 * preferences API instead of pulling DataStore and okio onto the desktop classpath.
 */
interface SettingsRepository {
    val isMusicEnabled: Flow<Boolean>
    val isSoundEnabled: Flow<Boolean>

    /** Whether a hit buzzes the phone, or the controller in use if it can rumble; on until set. */
    val isVibrationEnabled: Flow<Boolean>

    /** How the game is fitted to a screen that is not its shape; [DisplayMode.DEFAULT] until set. */
    val displayMode: Flow<DisplayMode>

    /** Whether the menu is drawn light or dark; [ThemeMode.DEFAULT], following the system, until set. */
    val themeMode: Flow<ThemeMode>

    suspend fun setMusicEnabled(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun setDisplayMode(mode: DisplayMode)
    suspend fun setThemeMode(mode: ThemeMode)
}
