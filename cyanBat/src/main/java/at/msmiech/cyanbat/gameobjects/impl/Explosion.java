package at.msmiech.cyanbat.gameobjects.impl;

import java.util.List;

import android.graphics.Rect;
import at.grueneis.game.framework.Input.TouchEvent;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Pixmap;
import at.msmiech.cyanbat.gameobjects.PixmapGameObject;

public class Explosion extends PixmapGameObject {

	private float ANIM_TICK_INTERVAL = 0.3f;
	private float animTickTime = 0.0f;
	private int animTick;
	public static final int realWidth = 25;
	private int srcX;

	public Explosion(Rect rect, Pixmap pixmap) {
		super(rect, pixmap);
		velocity.x = -1;
	}

	@Override
	public void update(float deltaTime, List<TouchEvent> touchEvents) {
		updateAnimation(deltaTime);
		super.update(deltaTime, touchEvents);
	}

	private void updateAnimation(float deltaTime) {
		animTickTime += deltaTime;
		if (animTickTime > ANIM_TICK_INTERVAL) {
			animTick = (animTick + 1) % 5;
			animTickTime -= ANIM_TICK_INTERVAL;
		}
		switch (animTick) {
		case 0:
			srcX = 0;
			break;
		case 1:
			srcX = 50;
			break;
		case 2:
			srcX = 115;
			break;
		case 3:
			srcX = 180;
			break;
		case 4:
			srcX = 245;
			removeMe = true;
			break;
		}
	}

	@Override
	public void draw(Graphics g) {
		g.drawPixmap(pixmap, rect.left, rect.top, srcX, 0, realWidth,
				rect.bottom);
	}

}
