package at.smiech.cyanbat.data

import at.smiech.cyanbat.Difficulty
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

    /**
     * Whether the game is in a window, in full screen or borderless; see [WindowMode]. Only a
     * desktop has a window to leave, so only there does Settings offer it. The desktop keeps it in
     * step with the window, which the player can also take in and out of full screen with a key or
     * the system's own controls.
     */
    val windowMode: Flow<WindowMode>

    /** Whether the menu is drawn light or dark; [ThemeMode.DEFAULT], following the system, until set. */
    val themeMode: Flow<ThemeMode>

    /**
     * Which language the game is in; [AppLanguage.DEFAULT], following the system, until set. A
     * host puts a new one into effect before this emits it, so whatever is drawn on hearing of it
     * comes out in it.
     */
    val language: Flow<AppLanguage>

    /**
     * The difficulty the player last started a stage on, where the cursor starts the next time
     * they are asked; [Difficulty.DEFAULT] until they have been. Not offered in Settings: the
     * difficulty is chosen as each stage starts, and most players settle on one.
     */
    val difficulty: Flow<Difficulty>

    /**
     * How a finger steers the bat on a touch screen, or null until the player has said, which a run
     * flies as [TouchSteering.DEFAULT]. Null is what has the menu ask, before the first run on a
     * touch screen; Settings shows it as the default.
     */
    val touchSteering: Flow<TouchSteering?>

    /** How far the stick on the screen has to be pushed; [StickSensitivity.DEFAULT] until set. */
    val stickSensitivity: Flow<StickSensitivity>

    suspend fun setMusicEnabled(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun setDisplayMode(mode: DisplayMode)
    suspend fun setWindowMode(mode: WindowMode)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setDifficulty(difficulty: Difficulty)
    suspend fun setTouchSteering(steering: TouchSteering)
    suspend fun setStickSensitivity(sensitivity: StickSensitivity)
}
