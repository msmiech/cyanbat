package at.smiech.cyanbat.util

import at.smiech.cyanbat.activities.CyanBatGameActivity

/**
 * A wrapper for music related operations
 */
class MusicPlayer {
    private var enabled = true // Enabled by default

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun isEnabled(enabled: Boolean): Boolean {
        return enabled
    }


    fun stopMusic() {
        if (CyanBatGameActivity.gameTrack.isPlaying)
            CyanBatGameActivity.gameTrack.stop()
        CyanBatGameActivity.gameTrack.isLooping = false
    }

    fun continueMusic() {
        if (!enabled)
            return
        CyanBatGameActivity.gameTrack.play()
        CyanBatGameActivity.gameTrack.isLooping = true
    }
}
