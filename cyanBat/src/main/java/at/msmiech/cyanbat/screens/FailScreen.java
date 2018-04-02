package at.msmiech.cyanbat.screens;

import java.util.List;

import at.grueneis.game.framework.Game;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Input.TouchEvent;
import at.msmiech.cyanbat.CyanBatGame;

public class FailScreen extends CyanBatBaseScreen {

	private List<TouchEvent> touchEvents;
	private Graphics g;

	public FailScreen(Game game) {
		super(game);
		init();

	}

	private void init() {
		g = game.getGraphics();
		CyanBatGame.ShowAds();
		System.gc();
		initSounds();
	}

	private void initSounds() {
		if (CyanBatGame.gameTrack.isPlaying()) {
			CyanBatGame.gameTrack.stop();
			CyanBatGame.gameTrack.setLooping(false);
		}
	}

	@Override
	public void update(float deltaTime) {
		touchEvents = game.getInput().getTouchEvents();
		for (TouchEvent event : touchEvents) {
			if (event.type == TouchEvent.TOUCH_UP) {
				CyanBatGame.vib.vibrate(250);
				game.setScreen(new StartScreen(game));
			}
		}
		super.update(deltaTime);
	}

	@Override
	public void present(float deltaTime) {
		drawMap(g);
		g.drawPixmap(CyanBatGame.gameOver, 0, 0);
	}

	@Override
	public void pause() {

	}

	@Override
	public void resume() {

	}

	@Override
	public void dispose() {

	}

}
