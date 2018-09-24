package at.smiech.cyanbat.activities;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.Vibrator;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Graphics.PixmapFormat;
import at.grueneis.game.framework.Music;
import at.grueneis.game.framework.Pixmap;
import at.grueneis.game.framework.Screen;
import at.grueneis.game.framework.Sound;
import at.grueneis.game.framework.code.AndroidGameActivity;
import at.smiech.cyanbat.screens.GameScreen;
import at.smiech.cyanbat.util.MusicPlayer;

public class CyanBatGameActivity extends AndroidGameActivity {

    public static final String TAG = "CyanBatGameActivity";
    public static final boolean DEBUG = false;
    public static final boolean SHOOTING_ENABLED = false;

    public static Pixmap bat;
    public static Pixmap death;
    public static Pixmap menuButtons;
    public static Pixmap mainMenu;
    public static Pixmap gameOver;
    public static Pixmap background;
    public static Pixmap[] topObstacles = new Pixmap[2];
    public static Pixmap[] bottomObstacles = new Pixmap[2];
    public static Music gameTrack;
    public static Sound deathSound;
    public static Music gameOverMusic;
    public static Pixmap enemies;
    public static Vibrator vib;
    public static MusicPlayer musicPlayer;
    public static Pixmap explosion;
    public static Pixmap shot;
    public static CyanBatGameActivity currentActivity = null;

    public Screen getStartScreen() {
        if (DEBUG)
            Log.d(TAG, "getStartScreen");
        currentActivity = this;
        initAssets();
        return new GameScreen(this);
    }

    private void initAssets() {
        if (DEBUG)
            Log.d(TAG, "initAssets");

        Graphics g = getGraphics();
        // Loading image assets
        bat = g.newPixmap("cyanBat.png", PixmapFormat.ARGB4444);
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
        death = g.newPixmap("death.png", PixmapFormat.ARGB4444);
        enemies = g.newPixmap("enemies.png", PixmapFormat.ARGB4444);
        explosion = g.newPixmap("explosion.png", PixmapFormat.ARGB4444);
        shot = g.newPixmap("shot.png", PixmapFormat.ARGB4444);

        musicPlayer = new MusicPlayer();
        gameTrack = getAudio().newMusic("game_theme.mp3");
        gameOverMusic = getAudio().newMusic("game_over.mp3");
        deathSound = getAudio().newSound("deathSound.mp3");

        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setNavigationBarColor(Color.BLACK);
        }

        super.onCreate(savedInstanceState, persistentState);
    }
}
