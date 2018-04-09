package at.msmiech.cyanbat.threads;

import java.util.List;
import java.util.Random;

import android.util.Log;

import at.msmiech.cyanbat.CyanBatGame;
import at.msmiech.cyanbat.interfaces.GameObject;
import at.msmiech.cyanbat.objects.gameobjects.Enemy;
import at.msmiech.cyanbat.screens.CyanBatBaseScreen;

public class EnemyGenerator extends Thread {
    private static final boolean DEBUG = false;
    private static final String TAG = CyanBatGame.TAG;
    private final List<GameObject> gameObjects;
    private final Random rnd = new Random();
    private int realEnemyHeight = CyanBatGame.enemies.getHeight();

    public EnemyGenerator(List<GameObject> gameObjects) {
        this.gameObjects = gameObjects;
    }

    @Override
    public void run() {
        try {
            while (!interrupted()) {

                sleep(rnd.nextInt(500) * 10);
                generateEnemy();
            }
        } catch (InterruptedException e) {
            this.interrupt();
        }
        super.run();
    }

    private void generateEnemy() {
        if (DEBUG)
            Log.d(TAG, "generateObstacle");
        synchronized (gameObjects) {
            gameObjects.add(new Enemy(CyanBatBaseScreen.DISPLAY_HEIGHT, rnd
                    .nextInt(200) + 100, Enemy.realWidth, realEnemyHeight,
                    CyanBatGame.enemies, rnd.nextInt(3)));
        }
    }
}
