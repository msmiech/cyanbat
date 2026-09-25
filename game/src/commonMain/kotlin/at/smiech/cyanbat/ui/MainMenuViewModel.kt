package at.smiech.cyanbat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.SettingsRepository
import at.smiech.engine.Music
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Menu music, the music setting, and which stages are open.
 *
 * A plain multiplatform [ViewModel]; the old AndroidViewModel(Application) form has no KMP
 * equivalent, and the menu track now goes through the engine's [Music] abstraction rather than
 * MediaPlayer, so desktop gets the same behavior.
 *
 * Shared by the main screen and the stage select - both ask for it from the same owner - so the
 * menu track is one track across the two, and stopping it from either stops it.
 */
class MainMenuViewModel(
    settings: SettingsRepository,
    private val menuMusic: Music?,
    stageUnlocks: StageUnlockStore,
) : ViewModel() {

    val isMusicEnabled: StateFlow<Boolean> = settings.isMusicEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

    /**
     * The highest stage open to the player. Eager, so Start Game knows where to go the moment it
     * is pressed rather than on whatever frame the store's first read lands.
     */
    val highestUnlocked: StateFlow<Int> = stageUnlocks.highestUnlocked
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    fun startMusic() {
        if (!isMusicEnabled.value) return
        menuMusic?.apply {
            isLooping = true
            play()
        }
    }

    fun stopMusic() {
        menuMusic?.takeIf { it.isPlaying }?.pause()
    }

    override fun onCleared() {
        super.onCleared()
        menuMusic?.dispose()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
