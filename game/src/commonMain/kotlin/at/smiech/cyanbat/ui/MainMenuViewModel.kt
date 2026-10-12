package at.smiech.cyanbat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.SettingsRepository
import at.smiech.engine.Music
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Menu music, the music setting, which stages are open, and each stage's highscore and best clear.
 * The menu track plays through the engine's [Music], so both platforms behave alike.
 *
 * Shared by the main screen and the stage select, which ask the same owner for it, so the menu
 * track is one track across the two, and stopping it from either stops it.
 */
class MainMenuViewModel(
    private val settings: SettingsRepository,
    private val menuMusic: Music?,
    stageUnlocks: StageUnlockStore,
    highscores: HighscoreStore,
) : ViewModel() {

    /** Whether music is on; the menu track plays only while it is. */
    val isMusicEnabled: StateFlow<Boolean> = settings.isMusicEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

    /**
     * The highest stage open to the player. Eager, so Start Game knows where to go the moment it
     * is pressed rather than on whatever frame the store's first read lands.
     */
    val highestUnlocked: StateFlow<Int> = stageUnlocks.highestUnlocked
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    /**
     * Each stage's highscore, by stage id. Eager as well, so the stage select opens with the
     * scores already on its cards rather than a frame of zeros ahead of them.
     */
    val highscores: StateFlow<Map<Int, Int>> = highscores.byStage
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The hardest difficulty each stage has been cleared on, for its stars. Eager, as the scores. */
    val clears: StateFlow<Map<Int, Difficulty>> = stageUnlocks.clears
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * The difficulty the player last started a stage on, where the cursor starts the next time they
     * are asked. Kept with the settings rather than here: on Android the menu is made again for
     * every return from a run.
     */
    val lastDifficulty: StateFlow<Difficulty> = settings.difficulty
        .stateIn(viewModelScope, SharingStarted.Eagerly, Difficulty.DEFAULT)

    /** Keeps [difficulty] as the one the player last started a stage on. */
    fun rememberDifficulty(difficulty: Difficulty) {
        viewModelScope.launch { settings.setDifficulty(difficulty) }
    }

    /** Starts the menu track, looping, if music is on. */
    fun startMusic() {
        if (!isMusicEnabled.value) return
        menuMusic?.apply {
            isLooping = true
            play()
        }
    }

    /** Pauses the menu track, if it is playing. */
    fun stopMusic() {
        menuMusic?.takeIf { it.isPlaying }?.pause()
    }

    override fun onCleared() {
        super.onCleared()
        menuMusic?.dispose()
    }

    private companion object {
        /**
         * How long the music setting stays collected once nothing observes it, as
         * across a rotation.
         */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
