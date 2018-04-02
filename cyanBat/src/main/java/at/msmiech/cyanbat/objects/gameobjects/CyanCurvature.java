package at.msmiech.cyanbat.objects.gameobjects;

import java.util.List;

import android.graphics.Color;
import android.graphics.Rect;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Input.TouchEvent;

public class CyanCurvature extends AbstractGameObject {
	private final float TICK_INITIAL = 0.008f;
	private float tickTime = 0;
	private float tick = TICK_INITIAL;
	private int color = Color.CYAN;

	public CyanCurvature(Rect rect) {
		super(rect);
	}

	@Override
	public void update(float deltaTime, List<TouchEvent> touchEvents) {
		tickTime += deltaTime;
		while (tickTime > tick) {
			tickTime -= tick;
			if (Color.alpha(color) <= 1)
				removeMe = true;
			color = Color.argb(Color.alpha(color) - 1, Color.red(color),
					Color.green(color), Color.blue(color));
		}
		velocity.x = -2;
		super.update(deltaTime, touchEvents);
	}

	@Override
	public void draw(Graphics g) {
		g.drawRect(rect.left, rect.top, rect.width(), rect.height(), color);
	}
}
