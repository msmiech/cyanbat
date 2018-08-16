package at.smiech.cyanbat.gameobjects.impl;

import java.util.List;

import android.graphics.Rect;
import android.util.Log;
import at.grueneis.game.framework.Input.TouchEvent;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Pixmap;
import at.smiech.cyanbat.gameobjects.Collidable;
import at.smiech.cyanbat.gameobjects.PixmapGameObject;

public class Obstacle extends PixmapGameObject implements Collidable {

	public Obstacle(int x, int y, Pixmap pm) {
		super(new Rect(x, y, x + pm.getWidth(), y + pm.getHeight()), pm);
		velocity.x = -1;
	}

	@Override
	public void update(float deltaTime, List<TouchEvent> touchEvents) {
		if (DEBUG)
			Log.d(TAG, "updateObstacle");
		super.update(deltaTime, touchEvents);
	}

	public void hit() {
		removeMe = true;
	}
	@Override
	public void draw(Graphics g) {
		g.drawPixmap(pixmap, rect.left, rect.top);
		super.draw(g);
	}

}
