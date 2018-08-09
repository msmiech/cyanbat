package at.msmiech.cyanbat.gameobjects;

import java.util.List;

import android.graphics.Rect;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Input.TouchEvent;
import at.grueneis.game.framework.Pixmap;

public abstract class PixmapGameObject extends MovableGameObject {

	public Pixmap pixmap;

	protected PixmapGameObject(Rect rect, Pixmap pixmap) {
		super(rect);
		this.pixmap = pixmap;
	}

	@Override
	public void update(float deltaTime, List<TouchEvent> touchEvents) {
		super.update(deltaTime, touchEvents);
	}

	@Override
	public void draw(Graphics g) {
		super.draw(g);
	}
}
