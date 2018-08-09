package at.msmiech.cyanbat.service;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import android.util.Log;

import at.grueneis.game.framework.Pixmap;
import at.msmiech.cyanbat.activities.CyanBatGameActivity;
import at.msmiech.cyanbat.gameobjects.GameObject;
import at.msmiech.cyanbat.gameobjects.impl.Obstacle;
import at.msmiech.cyanbat.screens.CyanBatBaseScreen;

public class ObstacleGenerator implements Runnable {

    private static final boolean DEBUG = false;
    private static final String TAG = CyanBatGameActivity.TAG;
    private static final int OBSTACLE_GENERATION_INTERVAL = 1000; // in ms
    private final List<GameObject> gameObjects;
    private final Random rnd = new Random();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private CollisionDetection collisionDetection;
    private int generationInterval = OBSTACLE_GENERATION_INTERVAL;

    public ObstacleGenerator(List<GameObject> gameObjects, int interval) {
        this.gameObjects = gameObjects;
        this.generationInterval = interval;
    }

    public ObstacleGenerator(List<GameObject> gameObjects) {
        this.gameObjects = gameObjects;
    }

    @Override
    public void run() {
        this.running.set(true);
        try {
            while (running.get()) {
                Thread.sleep(rnd.nextInt(generationInterval) + generationInterval);
                generateObstacle();
            }
        } catch (InterruptedException e) {
            this.running.set(false);
        }
    }

    private void generateObstacle() {
        if (DEBUG)
            Log.d(TAG, "generateObstacle");
        int obstacleHeight;
        Pixmap obstaclePixmap;
        if (rnd.nextBoolean()) // Top or not?
        {
            obstaclePixmap = CyanBatGameActivity.topObstacles[rnd
                    .nextInt(CyanBatGameActivity.topObstacles.length)];
            obstacleHeight = 0;

        } else {
            obstaclePixmap = CyanBatGameActivity.bottomObstacles[rnd
                    .nextInt(CyanBatGameActivity.bottomObstacles.length)];
            obstacleHeight = CyanBatBaseScreen.DISPLAY_WIDTH
                    - obstaclePixmap.getHeight();
        }
        synchronized (gameObjects) {
            Obstacle obs = new Obstacle(CyanBatBaseScreen.DISPLAY_HEIGHT,
                    obstacleHeight, obstaclePixmap);
            gameObjects.add(obs);
            if (collisionDetection != null) {
                collisionDetection.addObjectToCheck(obs);
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
