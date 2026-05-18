package at.smiech.cyanbat.ui

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.smiech.cyanbat.R
import at.smiech.cyanbat.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainMenuViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private var mediaPlayer: MediaPlayer? = null

    /**
     * StateFlow representing whether music is enabled.
     * Observations are shared among subscribers and survive configuration changes.
     */
    val isMusicEnabled: StateFlow<Boolean> = settingsRepository.isMusicEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * Start music playback using MediaPlayer. 
     * Handles the case where music is disabled or already playing.
     */
    fun startMusic() {
        if (!isMusicEnabled.value) return

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(getApplication(), R.raw.menu_theme).apply {
                isLooping = true
            }
        }
        
        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    /**
     * Stops and releases music playback.
     */
    fun stopMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        stopMusic()
    }
}
