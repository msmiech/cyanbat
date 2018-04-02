package at.msmiech.cyanbat.objects.gameobjects;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Rect;
import android.util.Log;
import at.grueneis.game.framework.Input.TouchEvent;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Pixmap;
import at.msmiech.cyanbat.CyanBatGame;
import at.msmiech.cyanbat.interfaces.Collidable;
import at.msmiech.cyanbat.interfaces.GameObject;
import at.msmiech.cyanbat.screens.CyanBatBaseScreen;
import at.msmiech.cyanbat.screens.FailScreen;
import at.msmiech.cyanbat.screens.GameScreen;

public class CyanBat extends PixmapGameObject implements Collidable {

	private float ANIM_TICK_INTERVAL = 0.2f;
	private final float TICK_INITIAL = 0.009f;
	private float animTickTime = 0;
	public boolean alive = true;
	public int lives = 3;
	private int animTick;
	private float tickTime = 0;
	private float tick = TICK_INITIAL;
	private int srcX;
	private GameScreen gs;
	private ArrayList<CyanCurvature> curvatures = new ArrayList<CyanCurvature>();
	public final static int realWidth = 45;

	public CyanBat(int x, int y, int width, int height, Pixmap pm, GameScreen gs) {
		super(new Rect(x, y, x + realWidth, y + height), pm);
		this.gs = gs;
		init();
	}

	private void init() {
		// TODO Auto-generated method stub

	}

	@Override
	public void update(float deltaTime, List<TouchEvent> touchEvents) {
		if (DEBUG)
			Log.d(TAG, "updateBat");
		updateLogic(deltaTime, touchEvents);
		updateAnimation(deltaTime);
		updateCurvatations();

		if (CyanBatGame.SHOOTING_ENABLED) {
			if (CyanBatBaseScreen.game.getInput().getTouchEvents().size() > 1)
				shoot();
		}
		super.update(deltaTime, touchEvents);
	}

	private void updateCurvatations() {
		Rect potentialRect = new Rect(rect.left,
				(rect.top + (rect.height() / 2)), rect.left + rect.width() / 4,
				rect.bottom);
		for (int i = 0; i < curvatures.size(); i++) {
			CyanCurvature curv = curvatures.get(i);
			if (curv == null || curv.removeMe) {
				curvatures.remove(curv);
				continue;
			}
			if (Rect.intersects(curv.rect, potentialRect)) {
				return;
			}
		}

		potentialRect.left -= 2;
		potentialRect.right -= 1;
		CyanCurvature go = new CyanCurvature(potentialRect);
		curvatures.add(go);
		gs.gameObjects.add(go);
	}

	private void shoot() {
		if (Shot.count > 1)
			return;
		AbstractGameObject shot = new Shot(new Rect(rect.left, rect.top,
				rect.left + CyanBatGame.shot.getWidth(), rect.top
						+ CyanBatGame.shot.getHeight()), CyanBatGame.shot, this);
		gs.gameObjects.add((GameObject) shot);
		gs.colChk.addObjectToCheck(shot);
	}

	private void updateAnimation(float deltaTime) {
		animTickTime += deltaTime;
		if (animTickTime > ANIM_TICK_INTERVAL) {
			animTick = (animTick + 1) % 2;
			animTickTime -= ANIM_TICK_INTERVAL;
		}
		switch (animTick) {
		case 0:
			srcX = 0;
			break;
		case 1:
			srcX = 50;
			break;
		}
	}

	private void updateLogic(float deltaTime, List<TouchEvent> touchEvents) {
		if (alive) {
			velocity.x = 0;
			velocity.y = 0;
			if (!touchEvents.isEmpty()) {
				tickTime += deltaTime;
				while (tickTime > tick) {
					tickTime -= tick;
					for (int i = 0; i < touchEvents.size(); i++) {
						TouchEvent touch = touchEvents.get(i);
						if (touch.type == TouchEvent.TOUCH_DRAGGED)
							move(touch);
					}

				}
			}
		} else {
			velocity.x = 0;
			velocity.y = 2;
			if (rect.top > CyanBatBaseScreen.DISPLAY_WIDTH)
				CyanBatBaseScreen.game
						.setScreen(new FailScreen(GameScreen.game));
		}
	}

	private void move(TouchEvent touch) {
		if (DEBUG)
			Log.d(TAG, "moveBat");
		if (touch.x > rect.left) {
			if (rect.right < CyanBatBaseScreen.DISPLAY_HEIGHT) {
				velocity.x = 3;
			}
		} else {
			if (rect.left > 0) {
				velocity.x = -3;
			}
		}
		if (touch.y > rect.bottom) {
			if (rect.bottom < CyanBatBaseScreen.DISPLAY_WIDTH) {
				velocity.y = 3;
			}
		} else {
			if (rect.top > 0) {
				velocity.y = -3;
			}
		}

	}

	@Override
	public void draw(Graphics g) {
		if (DEBUG)
			Log.d(TAG, "drawBat");
		g.drawPixmap(pixmap, rect.left, rect.top, srcX, 0, realWidth,
				rect.height());
		super.draw(g);
	}

	public void hit() {
		CyanBatGame.vib.vibrate(250);
		lives -= 1;
		if (lives < 1) {
			CyanBatGame.deathSound.play(100);
			alive = false;
			gs.saveHighscore();
			CyanBatGame.musicPlayer.stopMusic();
			gs.interruptThreads();
			CyanBatGame.gameOverMusic.play();
		}
	}
}
