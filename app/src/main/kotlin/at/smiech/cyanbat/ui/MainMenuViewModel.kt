package at.smiech.cyanbat.ui

import android.app.Application
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.smiech.cyanbat.R
import at.smiech.cyanbat.data.SettingsRepository
import at.smiech.cyanbat.util.TAG
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainMenuViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)

    private val mediaPlayerLazy = lazy {
        MediaPlayer.create(getApplication(), R.raw.menu_theme)?.apply {
            isLooping = true
        }
    }
    private val mediaPlayer: MediaPlayer? by mediaPlayerLazy

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
     * Start music playback. Uses lazy initialization to create the MediaPlayer only when needed.
     */
    fun startMusic() {
        if (!isMusicEnabled.value) return

        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting music", e)
        }
    }

    /**
     * Pauses and resets music playback if the MediaPlayer has been initialized.
     */
    fun stopMusic() {
        if (mediaPlayerLazy.isInitialized()) {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.pause()
                        it.seekTo(0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping music", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (mediaPlayerLazy.isInitialized()) {
            mediaPlayer?.release()
        }
    }
}
