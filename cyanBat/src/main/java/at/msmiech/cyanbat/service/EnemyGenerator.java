package at.msmiech.cyanbat.service;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import android.util.Log;

import at.msmiech.cyanbat.CyanBatGame;
import at.msmiech.cyanbat.gameobjects.GameObject;
import at.msmiech.cyanbat.gameobjects.impl.Enemy;
import at.msmiech.cyanbat.screens.CyanBatBaseScreen;

public class EnemyGenerator implements Runnable {
    private static final boolean DEBUG = false;
    private static final String TAG = CyanBatGame.TAG;
    private static final int DEFAULT_GENERATION_INTERVAL = 500; // in ms

    private final List<GameObject> gameObjects;
    private final Random rnd = new Random();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private int realEnemyHeight = CyanBatGame.enemies.getHeight();
    private CollisionDetection collisionDetection;
    private int generationInterval = DEFAULT_GENERATION_INTERVAL;
    private long startTime = System.currentTimeMillis();


    public EnemyGenerator(List<GameObject> gameObjects, int interval) {
        this.gameObjects = gameObjects;
        this.generationInterval = interval;
    }

    public EnemyGenerator(List<GameObject> gameObjects) {
        this.gameObjects = gameObjects;
    }

    @Override
    public void run() {
        running.set(true);
        try {
            while (running.get()) {
                long elapsedTime = (System.currentTimeMillis() - startTime);
                long genSleepTime = Math.max(rnd.nextInt(generationInterval) * 10 - (elapsedTime / 100), generationInterval / 2);
                Thread.sleep(genSleepTime); // gets more difficult over time, i.e. gen sleeps less
                generateEnemy();
            }
        } catch (InterruptedException e) {
            this.running.set(false);
        }
    }

    private void generateEnemy() {
        if (DEBUG)
            Log.d(TAG, "generateObstacle");
        synchronized (gameObjects) {
            Enemy en = new Enemy(CyanBatBaseScreen.DISPLAY_HEIGHT, rnd
                    .nextInt(200) + 100, Enemy.realWidth, realEnemyHeight,
                    CyanBatGame.enemies, rnd.nextInt(3));
            gameObjects.add(en);
            if (collisionDetection != null) {
                collisionDetection.addObjectToCheck(en);
            }
        }
    }

    public void setCollisionDetection(CollisionDetection collisionDetection) {
        this.collisionDetection = collisionDetection;
    }

    public void stop() {
        if (!running.get()) {
            Log.v(TAG, "Generator is not running!");
        }
        running.set(false);
    }
}
