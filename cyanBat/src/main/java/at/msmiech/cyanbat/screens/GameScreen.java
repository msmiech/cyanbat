package at.msmiech.cyanbat.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.util.Log;

import at.grueneis.game.framework.Game;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Input.TouchEvent;
import at.msmiech.cyanbat.activities.CyanBatGameActivity;
import at.msmiech.cyanbat.gameobjects.GameObject;
import at.msmiech.cyanbat.service.CollisionDetection;
import at.msmiech.cyanbat.util.MusicPlayer;
import at.msmiech.cyanbat.gameobjects.impl.Background;
import at.msmiech.cyanbat.gameobjects.impl.CyanBat;
import at.msmiech.cyanbat.gameobjects.impl.Shot;
import at.msmiech.cyanbat.service.EnemyGenerator;
import at.msmiech.cyanbat.service.ObstacleGenerator;

public class GameScreen extends CyanBatBaseScreen {
    private static final String TAG = CyanBatGameActivity.TAG;

    public static final boolean DEBUG = CyanBatGameActivity.DEBUG;
    public final List<GameObject> gameObjects = new ArrayList<>();
    private CyanBat bat = new CyanBat(CyanBatBaseScreen.DISPLAY_HEIGHT / 3,
            CyanBatBaseScreen.DISPLAY_WIDTH / 2, CyanBat.DEFAULT_WIDTH,
            CyanBatGameActivity.bat.getHeight(), CyanBatGameActivity.bat, this);

    static final float TICK_INITIAL = 0.019f;
    static final float TICK_DECREAMENT_FACTOR = 0.9f;
    float tickTime = 0;
    static float tick = TICK_INITIAL;


    public static Random rnd;
    public static int highscore = 0;
    private static SharedPreferences.Editor prefEditor;
    private List<TouchEvent> touchEvents;
    private Graphics g;
    private Thread obstclGenThread;
    private ObstacleGenerator obstclGen;
    private Thread enmGenThread;
    private EnemyGenerator enmGen;
    public int score;
    public CollisionDetection colChk;
    private SharedPreferences prefs;
    private MusicPlayer musicPlayer = CyanBatGameActivity.musicPlayer;

    public GameScreen(Game game) {
        super(game);
        init();
    }

    private void init() {
        if (DEBUG)
            Log.d(TAG, "init");
        g = game.getGraphics();
        rnd = new Random();

        gameObjects.add(new Background(0, 0, CyanBatGameActivity.background,
                gameObjects)); // Add the first background
        gameObjects.add(bat); // Add the activity_main player character

        colChk = new CollisionDetection(gameObjects);
        colChk.addObjectToCheck(bat);
        Shot.count = 0;
        musicPlayer.continueMusic();
        initStats();
        startThreads();
    }

    private void initStats() {
        score = 0;
        Context ctx = game.getContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefEditor = prefs.edit();
        readHighscore();
    }

    private void readHighscore() {
        highscore = prefs.getInt("highscore", 0);
    }

    @Override
    public void update(float deltaTime) {
        if (DEBUG)
            Log.d(TAG, "update");
        touchEvents = game.getInput().getTouchEvents();
        tickTime += deltaTime;
        while (tickTime > tick) {
            tickTime -= tick;
            updateGameObjects(deltaTime);
            score++;
        }
        super.update(deltaTime);
    }


    // a simple gameloop, essentially
    private void updateGameObjects(float deltaTime) {
        if (DEBUG)
            Log.d(TAG, "updateGameObjects");
        Background.count = 0;
        synchronized (gameObjects) {
            for (int i = 0; i < gameObjects.size(); i++) {
                GameObject go = gameObjects.get(i);
                // Check for remove:
                if (go.scheduledForRemoval()) {
                    // Let's give the garbage collector something to do:
                    if (go instanceof Background)
                        Background.count -= 1;
                    if (go instanceof Shot)
                        Shot.count -= 1;
                    gameObjects.remove(go);
                }

                if (bat.alive)
                    colChk.checkCollisions();

                go.update(deltaTime, touchEvents);

            }
        }
    }

    public void saveHighscore() {
        if (score > highscore)
            highscore = score;
        prefEditor.putInt("highscore", highscore);
        prefEditor.commit();
    }

    @Override
    public void present(float deltaTime) {
        if (DEBUG)
            Log.d(TAG, "present");
        clearScreen();
        drawGameObjects();
        drawStats();
        if (!bat.alive)
            g.drawPixmap(CyanBatGameActivity.death, 15, 15);
    }

    private void drawStats() {
        if (DEBUG)
            Log.d(TAG, "drawStats");
        g.drawString("Score: " + score, 5, 20, 15, Color.CYAN);
        g.drawString("Highscore: " + highscore, 5, 40, 15, Color.CYAN);
        g.drawString("Lives: " + bat.lives, 5, 60, 15, Color.CYAN);

    }

    private void clearScreen() {
        drawMap(g); // Clear screen
    }

    private void drawGameObjects() {
        if (DEBUG)
            Log.d(TAG, "drawGameObjects");
        synchronized (gameObjects) {
            for (int i = 0; i < gameObjects.size(); i++) {
                gameObjects.get(i).draw(g);
            }
        }
    }

    @Override
    public void pause() {
        if (DEBUG)
            Log.d(TAG, "pause");
        CyanBatGameActivity.musicPlayer.stopMusic();
        interruptThreads();
        super.pause();
    }

    public void interruptThreads() {
        if (DEBUG)
            Log.d(TAG, "interruptThreads");
        obstclGen.stop();
        enmGen.stop();
    }

    @Override
    public void resume() {
        if (DEBUG)
            Log.d(TAG, "resume");
        initStats();
        startThreads();
        super.resume();
    }

    private void startThreads() {
        if (DEBUG)
            Log.d(TAG, "startThreads");
        initThreads();
        obstclGenThread.start();
        enmGenThread.start();
    }

    private void initThreads() {
        if (DEBUG)
            Log.d(TAG, "initThreads");
        if(obstclGenThread != null && obstclGenThread.isAlive()) {
            obstclGenThread.interrupt();
        }

        obstclGen = new ObstacleGenerator(gameObjects);
        obstclGen.setCollisionDetection(colChk);
        obstclGenThread = new Thread(obstclGen);


        if(enmGenThread != null && enmGenThread.isAlive()) {
            enmGenThread.interrupt();
        }
        enmGen = new EnemyGenerator(gameObjects);
        enmGen.setCollisionDetection(colChk);
        enmGenThread = new Thread(enmGen);
    }
}