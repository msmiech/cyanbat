package at.msmiech.cyanbat.util;

import at.msmiech.cyanbat.CyanBatGame;

public class MusicPlayer {
	public static boolean enabled = true;

	public void stopMusic() {
		if (!enabled)
			return;
		if (CyanBatGame.gameTrack.isPlaying())
			CyanBatGame.gameTrack.stop();
		CyanBatGame.gameTrack.setLooping(false);
	}

	public void continueMusic() {
		if (!enabled)
			return;
		if (CyanBatGame.menuTheme.isPlaying()) {
			CyanBatGame.menuTheme.stop();
			CyanBatGame.menuTheme.setLooping(false);
		}
		CyanBatGame.gameTrack.play();
		CyanBatGame.gameTrack.setLooping(true);
	}
}
