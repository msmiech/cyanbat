package at.msmiech.cyanbat.objects.gameobjects;

import java.util.List;

import android.graphics.Rect;
import android.util.Log;
import at.grueneis.game.framework.Input.TouchEvent;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Pixmap;
import at.msmiech.cyanbat.CyanBatGame;
import at.msmiech.cyanbat.interfaces.GameObject;
import at.msmiech.cyanbat.screens.CyanBatBaseScreen;

public class Background extends PixmapGameObject {
	public static int count = 0;
	private List<GameObject> gameObjects;

	public Background(int x, int y, Pixmap pm, List<GameObject> gameObjects) {
		super(new Rect(x, y, x + pm.getWidth(), y + pm.getHeight()), pm);
		this.gameObjects = gameObjects;
		velocity.x = -2;
	}

	@Override
	public void update(float deltaTime, List<TouchEvent> touchEvents) {
		if (DEBUG)
			Log.d(TAG, "updateBackground");
		Background.count += 1;
		if (Background.count < 2) {
			int bgArea = rect.right;
			if (bgArea - 5 < CyanBatBaseScreen.DISPLAY_HEIGHT) {
				gameObjects.add(0, new Background(
						CyanBatBaseScreen.DISPLAY_HEIGHT, 0,
						CyanBatGame.background, gameObjects));
			}
		}
		super.update(deltaTime, touchEvents);
	}

	@Override
	public void draw(Graphics g) {
		if (DEBUG)
			Log.d(TAG, "drawBackground");
		g.drawPixmap(pixmap, rect.left, rect.top);
	}
}
