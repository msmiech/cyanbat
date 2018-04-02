package at.msmiech.cyanbat;

import android.content.Context;
import android.os.Vibrator;
import android.util.Log;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Graphics.PixmapFormat;
import at.grueneis.game.framework.Music;
import at.grueneis.game.framework.Pixmap;
import at.grueneis.game.framework.Screen;
import at.grueneis.game.framework.Sound;
import at.grueneis.game.framework.code.AndroidGame;
import at.msmiech.cyanbat.objects.MusicPlayer;
import at.msmiech.cyanbat.screens.StartScreen;

public class CyanBatGame extends AndroidGame {

	public static final String TAG = "mSmiech_CyanBat";
	public static final boolean DEBUG = false;
	public static final boolean SHOW_ADS = true;
	public static final boolean SHOOTING_ENABLED = false;

	public static Pixmap bat;
	public static Pixmap death;
	public static Pixmap menuButtons;
	public static Pixmap mainMenu;
	public static Pixmap gameOver;
	public static Pixmap background;
	public static Pixmap[] topObstacles = new Pixmap[2];
	public static Pixmap[] bottomObstacles = new Pixmap[2];
	public static Music menuTheme;
	public static Music gameTrack;
	public static Sound deathSound;
	public static Music gameOverMusic;
	public static Pixmap enemies;
	public static Vibrator vib;
	public static MusicPlayer musicPlayer;
	public static Pixmap explosion;
	public static Pixmap shot;
	public static CyanBatGame currentActivity = null;

	// DO NOT MODIFY! DO NOT DISTRIBUTE! You are a closed alpha beta tester and
	// not even meant to see the code. This class contains the games resources.

	public Screen getStartScreen() {
		if (DEBUG)
			Log.d(TAG, "getStartScreen");
		currentActivity = this;
		initAssets();
		if (SHOW_ADS)
			initAds();
		return new StartScreen(this);
	}

	private void initAds() {
		if (DEBUG)
			Log.d(TAG, "initAds");
		/**
		 * adView = (AdView) findViewById(R.id.adView); adView.bringToFront();
		 * adView.setVisibility(AdView.GONE);
		 */
	}

	private void initAssets() {
		if (DEBUG)
			Log.d(TAG, "initAssets");
		Graphics g = getGraphics();
		bat = g.newPixmap("cyanBat.png", PixmapFormat.ARGB4444);
		musicPlayer = new MusicPlayer();
		mainMenu = g.newPixmap("menuBackground.png", PixmapFormat.ARGB4444);
		menuButtons = g.newPixmap("menuButtons.png", PixmapFormat.ARGB4444);
		gameOver = g.newPixmap("gameover.png", PixmapFormat.ARGB4444);
		background = g.newPixmap("background.jpg", PixmapFormat.ARGB4444);
		topObstacles[0] = g
				.newPixmap("topObstacle1.png", PixmapFormat.ARGB4444);
		topObstacles[1] = g
				.newPixmap("topObstacle2.png", PixmapFormat.ARGB4444);
		bottomObstacles[0] = g.newPixmap("bottomObstacle1.png",
				PixmapFormat.ARGB4444);
		bottomObstacles[1] = g.newPixmap("bottomObstacle2.png",
				PixmapFormat.ARGB4444);
		menuTheme = getAudio().newMusic("cyanBatTheme.mp3");
		gameTrack = getAudio().newMusic("gameTrack.mp3");
		death = g.newPixmap("death.png", PixmapFormat.ARGB4444);
		deathSound = getAudio().newSound("deathSound.mp3");
		gameOverMusic = getAudio().newMusic("gameOverMusic.mp3");
		enemies = g.newPixmap("enemies.png", PixmapFormat.ARGB4444);
		explosion = g.newPixmap("explosion.png", PixmapFormat.ARGB4444);
		shot = g.newPixmap("shot.png", PixmapFormat.ARGB4444);
		vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
	}

	public static void ShowAds() {
		if (!SHOW_ADS)
			return;
		currentActivity.runOnUiThread(new Runnable() {

			public void run() {
				currentActivity.showAds();
			}
		});
	}

	public static void HideAds() {
		if (!SHOW_ADS)
			return;
		currentActivity.runOnUiThread(new Runnable() {

			public void run() {
				currentActivity.hideAds();
			}
		});
	}

	private void showAds() {
		if (DEBUG)
			Log.d(TAG, "showAds");
		/**
		 * AdRequest adRequest = new AdRequest(); adView.loadAd(adRequest);
		 * adView.setVisibility(AdView.VISIBLE);
		 */
	}

	private void hideAds() {
		if (DEBUG)
			Log.d(TAG, "hideAds");
		/**
		 * adView.setVisibility(AdView.GONE);
		 */
	}

	public Context getContext() {
		return currentActivity.getContext();
	}

}
