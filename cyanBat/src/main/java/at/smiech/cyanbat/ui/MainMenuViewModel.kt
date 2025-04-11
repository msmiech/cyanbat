package at.smiech.cyanbat.ui

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import at.smiech.cyanbat.PREFS_KEY_MUSIC
import at.smiech.cyanbat.R
import at.smiech.cyanbat.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MainMenuViewModel(application: Application) : AndroidViewModel(application) {
    private var mediaPlayer: MediaPlayer? = null
    private var isMediaPlayerReleased: Boolean = false

    /**
     * Retrieves the user preference about music playback from the DataStore.
     */
    fun isMusicEnabled(): Flow<Boolean> =
        getApplication<Application>().dataStore.data.map { it[PREFS_KEY_MUSIC] ?: true }

    /**
     * Start music playback using MediaPlayer. Creates a new instance of the MediaPlayer if it was
     * null or already released.
     */
    fun startMusic() {
        if (isMediaPlayerReleased || mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(getApplication(), R.raw.menu_theme)
            isMediaPlayerReleased = false
        }
        mediaPlayer?.apply {
            start()
            isLooping = true
        }
    }

    /**
     * Stops music if media player exists and has not been released already.
     */
    fun stopMusic() {
        if (!isMediaPlayerReleased) mediaPlayer?.apply {
            stop()
            release()
            isMediaPlayerReleased = true
        }
    }
}