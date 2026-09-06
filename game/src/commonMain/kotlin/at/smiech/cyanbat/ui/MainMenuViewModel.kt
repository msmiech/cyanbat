package at.smiech.cyanbat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.smiech.cyanbat.data.SettingsRepository
import at.smiech.engine.Music
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Menu music and the music setting.
 *
 * A plain multiplatform [ViewModel]; the old AndroidViewModel(Application) form has no KMP
 * equivalent, and the menu track now goes through the engine's [Music] abstraction rather than
 * MediaPlayer, so desktop gets the same behaviour.
 */
class MainMenuViewModel(
    settings: SettingsRepository,
    private val menuMusic: Music?,
) : ViewModel() {

    val isMusicEnabled: StateFlow<Boolean> = settings.isMusicEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

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
