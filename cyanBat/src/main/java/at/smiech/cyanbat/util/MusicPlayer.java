package at.smiech.cyanbat.util;

import at.smiech.cyanbat.activities.CyanBatGameActivity;

public class MusicPlayer {
    public static boolean enabled = true;

    public void stopMusic() {
        if (!enabled)
            return;
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
