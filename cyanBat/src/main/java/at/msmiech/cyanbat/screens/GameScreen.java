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
import at.msmiech.cyanbat.CyanBatGame;
import at.msmiech.cyanbat.interfaces.GameObject;
import at.msmiech.cyanbat.objects.CollisionDetection;
import at.msmiech.cyanbat.objects.MusicPlayer;
import at.msmiech.cyanbat.objects.gameobjects.AbstractGameObject;
import at.msmiech.cyanbat.objects.gameobjects.Background;
import at.msmiech.cyanbat.objects.gameobjects.CyanBat;
import at.msmiech.cyanbat.objects.gameobjects.Shot;
import at.msmiech.cyanbat.threads.EnemyGenerator;
import at.msmiech.cyanbat.threads.ObstacleGenerator;

public class GameScreen extends CyanBatBaseScreen {

    public static final boolean DEBUG = CyanBatGame.DEBUG;
    public final List<GameObject> gameObjects = new ArrayList<GameObject>();
    private CyanBat bat = new CyanBat(CyanBatBaseScreen.DISPLAY_HEIGHT / 3,
            CyanBatBaseScreen.DISPLAY_WIDTH / 2, CyanBat.realWidth,
            CyanBatGame.bat.getHeight(), CyanBatGame.bat, this);

    static final float TICK_INITIAL = 0.019f;
    static final float TICK_DECREAMENT_FACTOR = 0.9f;
    float tickTime = 0;
    static float tick = TICK_INITIAL;

    private static final String TAG = CyanBatGame.TAG;
    private List<TouchEvent> touchEvents;
    private Graphics g;
    private ObstacleGenerator obstclGen;
    private EnemyGenerator enmGen;
    public int score;
    public static Random rnd;
    public CollisionDetection colChk;
    public static int highscore = 0;
    private static SharedPreferences.Editor prefEditor;
    private SharedPreferences prefs;
    private MusicPlayer musicPlayer = CyanBatGame.musicPlayer;

    public GameScreen(Game game) {
        super(game);
        init();
    }

    private void init() {
        if (DEBUG)
            Log.d(TAG, "init");
        g = game.getGraphics();
        rnd = new Random();
        gameObjects.add(new Background(0, 0, CyanBatGame.background,
                gameObjects));
        colChk = new CollisionDetection(gameObjects);
        colChk.addObjectToCheck(bat);
        gameObjects.add(bat);
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

    private void updateGameObjects(float deltaTime) {
        if (DEBUG)
            Log.d(TAG, "updateGameObjects");
        Background.count = 0;
        synchronized (gameObjects) {
            for (int i = 0; i < gameObjects.size(); i++) {
                AbstractGameObject go = (AbstractGameObject) gameObjects.get(i);
                // Check for remove:
                if (!(go == null))
                    if (!go.removeMe) {
                        if (bat.alive)
                            colChk.checkCollisions();
                        go.update(deltaTime, touchEvents);
                        continue;
                    }
                // Let's give the garbage collector something to do:
                if (go instanceof Background)
                    Background.count -= 1;
                if (go instanceof Shot)
                    Shot.count -= 1;
                go.rect = null;
                go.velocity = null;
                gameObjects.remove(go);
                go = null;
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
            g.drawPixmap(CyanBatGame.death, 15, 15);
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
        CyanBatGame.musicPlayer.stopMusic();
        interruptThreads();
        super.pause();
    }

    public void interruptThreads() {
        if (DEBUG)
            Log.d(TAG, "interruptThreads");
        obstclGen.interrupt();
        enmGen.interrupt();
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
        obstclGen.start();
        enmGen.start();
    }

    private void initThreads() {
        if (DEBUG)
            Log.d(TAG, "initThreads");
        obstclGen = new ObstacleGenerator(gameObjects);
        enmGen = new EnemyGenerator(gameObjects);
    }
}