package at.msmiech.cyanbat.threads;

import java.util.List;
import java.util.Random;

import android.util.Log;

import at.grueneis.game.framework.Pixmap;
import at.msmiech.cyanbat.CyanBatGame;
import at.msmiech.cyanbat.interfaces.GameObject;
import at.msmiech.cyanbat.objects.CollisionDetection;
import at.msmiech.cyanbat.objects.gameobjects.Obstacle;
import at.msmiech.cyanbat.screens.CyanBatBaseScreen;

public class ObstacleGenerator extends Thread {

    private static final boolean DEBUG = false;
    private static final String TAG = CyanBatGame.TAG;
    private static final int OBSTACLE_GENERATION_INTERVAL = 1000; // in ms
    private final List<GameObject> gameObjects;
    private final Random rnd = new Random();
    private CollisionDetection collisionDetection;

    public ObstacleGenerator(List<GameObject> gameObjects) {
        this.gameObjects = gameObjects;
    }

    @Override
    public void run() {
        try {
            while (!interrupted()) {
                sleep(rnd.nextInt(OBSTACLE_GENERATION_INTERVAL) + OBSTACLE_GENERATION_INTERVAL);
                generateObstacle();
            }
        } catch (InterruptedException e) {
            this.interrupt();
        }
        super.run();
    }

    private void generateObstacle() {
        if (DEBUG)
            Log.d(TAG, "generateObstacle");
        int obstacleHeight;
        Pixmap obstaclePixmap;
        if (rnd.nextBoolean()) // Top or not?
        {
            obstaclePixmap = CyanBatGame.topObstacles[rnd
                    .nextInt(CyanBatGame.topObstacles.length)];
            obstacleHeight = 0;

        } else {
            obstaclePixmap = CyanBatGame.bottomObstacles[rnd
                    .nextInt(CyanBatGame.bottomObstacles.length)];
            obstacleHeight = CyanBatBaseScreen.DISPLAY_WIDTH
                    - obstaclePixmap.getHeight();
        }
        synchronized (gameObjects) {
            Obstacle obs = new Obstacle(CyanBatBaseScreen.DISPLAY_HEIGHT,
                    obstacleHeight, obstaclePixmap);
            gameObjects.add(obs);
            if(collisionDetection != null) {
                collisionDetection.addObjectToCheck(obs);
            }
        }
    }

    public void setCollisionDetection(CollisionDetection collisionDetection) {
        this.collisionDetection = collisionDetection;
    }
}
