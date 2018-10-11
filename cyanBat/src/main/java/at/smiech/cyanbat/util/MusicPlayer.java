package at.smiech.cyanbat.util;

import at.smiech.cyanbat.activities.CyanBatGameActivity;

/**
 * A wrapper for music related operations
 */
public class MusicPlayer {
    private boolean enabled = true; // Enabled by default

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled(boolean enabled) {
        return enabled;
    }


    public void stopMusic() {
        if (CyanBatGameActivity.gameTrack.isPlaying())
            CyanBatGameActivity.gameTrack.stop();
        CyanBatGameActivity.gameTrack.setLooping(false);
    }

    public void continueMusic() {
        if (!enabled)
            return;
        CyanBatGameActivity.gameTrack.play();
        CyanBatGameActivity.gameTrack.setLooping(true);
    }
}
