package at.grueneis.game.framework.code;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import at.grueneis.game.framework.Audio;
import at.grueneis.game.framework.FileIO;
import at.grueneis.game.framework.Game;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Input;
import at.grueneis.game.framework.Screen;

/**
 * Android Game Framework implementation
 *
 * @author Robert Grüneis
 * <p>
 * Modifications by mart1n8891
 */
public abstract class AndroidGameActivity extends AppCompatActivity implements Game {
    private static final long WAKE_LOCK_TIMEOUT = 512L;
    public static boolean useWakeLock = false;
    AndroidFastRenderView renderView;
    Graphics graphics;
    Audio audio;
    Input input;
    FileIO fileIO;
    Screen screen;
    WakeLock wakeLock;
    RelativeLayout.LayoutParams layoutParams;
    RelativeLayout mainLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int frameBufferWidth = isLandscape ? 480 : 320;
        int frameBufferHeight = isLandscape ? 320 : 480;
        Bitmap frameBuffer = Bitmap.createBitmap(frameBufferWidth,
                frameBufferHeight, Bitmap.Config.RGB_565);
        int displayWidth = getWindowManager().getDefaultDisplay().getWidth();
        int displayHeight = getWindowManager().getDefaultDisplay().getHeight();
        float scaleX = (float) frameBufferWidth / displayWidth;
        float scaleY = (float) frameBufferHeight / displayHeight;

        setContentView(at.grueneis.game.framework.R.layout.main);
        mainLayout = findViewById(at.grueneis.game.framework.R.id.mainLayout);
        layoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);

        renderView = new AndroidFastRenderView(this, frameBuffer);
        graphics = new AndroidGraphics(getAssets(), frameBuffer);
        fileIO = new AndroidFileIO(getAssets());
        audio = new AndroidAudio(this);
        input = new AndroidInput(this, renderView, scaleX, scaleY);
        mainLayout.addView(renderView);
        screen = getStartScreen();

        if (useWakeLock) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK,
                        "Game");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (useWakeLock && wakeLock != null) {
            wakeLock.acquire(WAKE_LOCK_TIMEOUT);
        }
        screen.resume();
        renderView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (useWakeLock)
            wakeLock.release();
        renderView.pause();
        screen.pause();
        if (isFinishing())
            screen.dispose();
    }

    public Input getInput() {
        return input;
    }

    public FileIO getFileIO() {
        return fileIO;
    }

    public Graphics getGraphics() {
        return graphics;
    }

    public Audio getAudio() {
        return audio;
    }

    public void setScreen(Screen screen) {
        if (screen == null)
            throw new IllegalArgumentException("Screen is null!");
        this.screen.pause();
        this.screen.dispose();
        screen.resume();
        screen.update(0);
        this.screen = screen;
    }

    public Screen getCurrentScreen() {
        return screen;
    }

    public Context getContext() {
        return this;
    }

    public void addView(View v) {
        layoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        mainLayout.addView(v, layoutParams);
        mainLayout.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        mainLayout.bringToFront();
    }
}
