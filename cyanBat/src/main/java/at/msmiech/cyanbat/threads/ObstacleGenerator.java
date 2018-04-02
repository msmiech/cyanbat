package at.msmiech.cyanbat.threads;

import java.util.List;
import java.util.Random;

import android.util.Log;
import at.grueneis.game.framework.Pixmap;
import at.msmiech.cyanbat.CyanBatGame;
import at.msmiech.cyanbat.interfaces.GameObject;
import at.msmiech.cyanbat.objects.gameobjects.Obstacle;
import at.msmiech.cyanbat.screens.CyanBatBaseScreen;

public class ObstacleGenerator extends Thread {

	private static final boolean DEBUG = false;
	private static final String TAG = CyanBatGame.TAG;
	private Random rnd = new Random();
	private List<GameObject> gameObjects;

	public ObstacleGenerator(List<GameObject> gameObjects) {
		this.gameObjects = gameObjects;
	}

	@Override
	public void run() {
		try {
			while (!interrupted()) {
				sleep(rnd.nextInt(1000) + 1000);
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
		int obstacleHeight = 0;
		Pixmap obstaclePixmap = null;
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
			gameObjects.add(new Obstacle(CyanBatBaseScreen.DISPLAY_HEIGHT,
					obstacleHeight, obstaclePixmap));
		}
	}
}
