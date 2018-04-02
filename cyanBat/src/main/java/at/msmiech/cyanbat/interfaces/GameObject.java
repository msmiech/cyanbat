package at.msmiech.cyanbat.interfaces;

import java.util.List;

import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Input.TouchEvent;
import at.msmiech.cyanbat.CyanBatGame;

public interface GameObject {
	public static final String TAG = CyanBatGame.TAG;
	public static final boolean DEBUG = CyanBatGame.DEBUG;
	public void update(float deltaTime, List<TouchEvent> touchEvents);
	public void draw(Graphics g);
}
