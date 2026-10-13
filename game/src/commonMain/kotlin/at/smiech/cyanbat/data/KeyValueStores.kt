package at.smiech.cyanbat.data

import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.clearsBeforeDifficulties
import at.smiech.cyanbat.harderThanBest
import at.smiech.engine.DisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * The stores of a host that keeps what the game remembers as strings under keys - the browser's
 * local storage, iOS's user defaults - under the desktop's key names. Each flow is an in-memory
 * mirror, as on the desktop: the host is the only writer, so one instance of each has to be shared
 * between the menu and every run.
 *
 * The highscores are not migrated: these hosts came after the stages last moved, so whatever they
 * have stored is in today's order. The stages' clears are; see KeyValueStageUnlockStore.
 */

/**
 * Where a host keeps what the game remembers: strings under the game's own keys. A write that the
 * platform refuses is still to be read back for as long as the game runs.
 */
interface KeyValueStorage {
    /** Every key the game has stored. */
    val keys: Set<String>

    fun get(key: String): String?

    fun put(key: String, value: String)
}

fun KeyValueStorage.getInt(key: String, default: Int): Int = get(key)?.toIntOrNull() ?: default

fun KeyValueStorage.getBoolean(key: String, default: Boolean): Boolean =
    get(key)?.toBooleanStrictOrNull() ?: default

/** A [KeyValueStorage] that forgets everything when the game closes, for tests. */
class MemoryKeyValueStorage : KeyValueStorage {
    private val values = HashMap<String, String>()

    override val keys: Set<String> get() = values.keys

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String) {
        values[key] = value
    }
}

/**
 * [SettingsRepository] on a [KeyValueStorage]. Making one puts the language into effect, so the
 * host makes its one before the menu reads a string.
 *
 * The window mode is kept like the rest, although no host on these stores offers it: a page fills
 * the browser's window, and a phone or a tablet its screen.
 *
 * @param initialLanguage the language the player chose, where the platform keeps that itself (iOS
 *   has a per-app language of its own); otherwise the one stored here.
 * @param applyLanguage puts a language into effect, before the menu hears of it, so it redraws in
 *   it.
 */
class KeyValueSettingsRepository(
    private val storage: KeyValueStorage,
    initialLanguage: AppLanguage = AppLanguage.fromName(storage.get(KEY_LANGUAGE)),
    private val applyLanguage: (AppLanguage) -> Unit,
) : SettingsRepository {
    private val music = MutableStateFlow(storage.getBoolean(KEY_MUSIC, true))
    private val sound = MutableStateFlow(storage.getBoolean(KEY_SOUND, true))
    private val vibration = MutableStateFlow(storage.getBoolean(KEY_VIBRATION, true))
    private val display = MutableStateFlow(DisplayMode.fromName(storage.get(KEY_DISPLAY_MODE)))
    private val window = MutableStateFlow(WindowMode.fromName(storage.get(KEY_WINDOW_MODE)))
    private val theme = MutableStateFlow(ThemeMode.fromName(storage.get(KEY_THEME_MODE)))
    private val chosenLanguage = MutableStateFlow(initialLanguage)
    private val lastDifficulty = MutableStateFlow(Difficulty.fromName(storage.get(KEY_DIFFICULTY)))
    private val steering = MutableStateFlow(TouchSteering.fromName(storage.get(KEY_TOUCH_STEERING)))

    override val isMusicEnabled: Flow<Boolean> = music.asStateFlow()
    override val isSoundEnabled: Flow<Boolean> = sound.asStateFlow()
    override val isVibrationEnabled: Flow<Boolean> = vibration.asStateFlow()
    override val displayMode: Flow<DisplayMode> = display.asStateFlow()
    override val windowMode: Flow<WindowMode> = window.asStateFlow()
    override val themeMode: Flow<ThemeMode> = theme.asStateFlow()
    override val language: Flow<AppLanguage> = chosenLanguage.asStateFlow()
    override val difficulty: Flow<Difficulty> = lastDifficulty.asStateFlow()
    override val touchSteering: Flow<TouchSteering?> = steering.asStateFlow()

    init {
        applyLanguage(initialLanguage)
    }

    override suspend fun setMusicEnabled(enabled: Boolean) {
        storage.put(KEY_MUSIC, enabled.toString())
        music.value = enabled
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        storage.put(KEY_SOUND, enabled.toString())
        sound.value = enabled
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        storage.put(KEY_VIBRATION, enabled.toString())
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
        applyLanguage(language)
        chosenLanguage.value = language
    }

    override suspend fun setDifficulty(difficulty: Difficulty) {
        storage.put(KEY_DIFFICULTY, difficulty.name)
        lastDifficulty.value = difficulty
    }

    override suspend fun setTouchSteering(steering: TouchSteering) {
        storage.put(KEY_TOUCH_STEERING, steering.name)
        this.steering.value = steering
    }

    private companion object {
        const val KEY_MUSIC = "music_enabled"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_DISPLAY_MODE = "display_mode"
        const val KEY_WINDOW_MODE = "window_mode"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LANGUAGE = "language"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_TOUCH_STEERING = "touch_steering"
    }
}

/** [HighscoreStore] on a [KeyValueStorage], one key per stage. */
class KeyValueHighscoreStore(private val storage: KeyValueStorage) : HighscoreStore {
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
        storage.put(key, raised.toString())
        scores.value += stageId to raised
    }

    private companion object {
        /** A stage's score is kept under this and its id. */
        const val KEY_PREFIX = "highscore_stage_"
    }
}

/**
 * [StageUnlockStore] on a [KeyValueStorage], next to the highscores. Unlike the stages' order, the
 * difficulties came after the browser, so a store that kept unlocks before them takes the clears
 * they stand for, once, as the desktop does.
 */
class KeyValueStageUnlockStore(private val storage: KeyValueStorage) : StageUnlockStore {
    /** The highest stage open, mirrored from the store. */
    private val highest = MutableStateFlow(storage.getInt(KEY, 1).coerceAtLeast(1))

    init {
        if (!storage.getBoolean(CLEARS_KEPT, false)) {
            for ((stageId, difficulty) in clearsBeforeDifficulties(highest.value)) {
                raiseClear(stageId, difficulty)
            }
            storage.put(CLEARS_KEPT, true.toString())
        }
    }

    /** Every stage's best clear, mirrored from the store, read back off the key names. */
    private val cleared = MutableStateFlow(
        storage.keys
            .filter { it.startsWith(CLEARED_PREFIX) }
            .mapNotNull { key ->
                val stageId = key.removePrefix(CLEARED_PREFIX).toIntOrNull()
                val difficulty = Difficulty.ofStars(storage.getInt(key, 0))
                if (stageId != null && difficulty != null) stageId to difficulty else null
            }
            .toMap()
    )

    override val highestUnlocked: Flow<Int> = highest.asStateFlow()

    override val clears: Flow<Map<Int, Difficulty>> = cleared.asStateFlow()

    override fun unlockAsync(stageId: Int) {
        val raised = maxOf(storage.getInt(KEY, 1), stageId)
        storage.put(KEY, raised.toString())
        highest.value = raised
    }

    override fun recordClearAsync(stageId: Int, difficulty: Difficulty) {
        if (raiseClear(stageId, difficulty)) cleared.value += stageId to difficulty
    }

    /** Stores [difficulty] as [stageId]'s best clear if it beats it, and says whether it did. */
    private fun raiseClear(stageId: Int, difficulty: Difficulty): Boolean {
        val key = CLEARED_PREFIX + stageId
        if (!harderThanBest(Difficulty.ofStars(storage.getInt(key, 0)), difficulty)) return false
        storage.put(key, difficulty.stars.toString())
        return true
    }

    private companion object {
        const val KEY = "highest_stage_unlocked"
        const val CLEARED_PREFIX = "cleared_stage_"
        const val CLEARS_KEPT = "stage_clears_kept"
    }
}
