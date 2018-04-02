package at.msmiech.cyanbat.screens;

import java.util.List;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import at.grueneis.game.framework.Game;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Input.TouchEvent;
import at.msmiech.cyanbat.CyanBatGame;

public class StartScreen extends CyanBatBaseScreen {

	private Graphics g;

	public StartScreen(Game game) {
		super(game);
		init();
	}

	private void init() {
		g = game.getGraphics();
		System.gc();
		if (!CyanBatGame.menuTheme.isLooping()) {
			CyanBatGame.menuTheme.setLooping(true);
			CyanBatGame.menuTheme.play();
		}
	}

	@Override
	public void update(float deltaTime) {
		List<TouchEvent> touchEvents = game.getInput().getTouchEvents();
		for (TouchEvent event : touchEvents) {
			if (event.type == TouchEvent.TOUCH_UP) {
				if (event.x > 50 && event.x < 190 && event.y < 150
						&& event.y > 90) {
					CyanBatGame.vib.vibrate(250);
					game.setScreen(new GameScreen(game));
				}
				if (event.x > 20 && event.x < 210 && event.y < 50
						&& event.y > 25) {
					CyanBatGame.vib.vibrate(200);
					System.exit(0);

				}

			}
		}
		super.update(deltaTime);
	}

	@Override
	public void present(float deltaTime) {

		drawMap(g);
		g.drawPixmap(CyanBatGame.mainMenu, -2, -2);
		g.drawPixmap(CyanBatGame.menuButtons, 5, 5);
		Context context = ((CyanBatGame) game).getContext();
		try {
			g.drawString(
					"v" + context.getPackageManager().getPackageInfo(
							context.getPackageName(), 0).versionName + " [preview]", 5, 310, 15,
					Color.RED);
		} catch (NameNotFoundException e) {
			g.drawString(
					"NaN", 5, 310, 15,
					Color.RED);
		}
		// ChangeLog:
		// Shooting disabled due to performance problems.
		// Redone object processing... For some reason performance decrease
		// instead of increase
	}

	@Override
	public void pause() {
		if (CyanBatGame.menuTheme.isPlaying())
			CyanBatGame.menuTheme.stop();
		CyanBatGame.menuTheme.setLooping(false);
		super.pause();
	}

	@Override
	public void resume() {
		if (!CyanBatGame.menuTheme.isPlaying())
			CyanBatGame.menuTheme.play();
		CyanBatGame.menuTheme.setLooping(true);
		super.resume();
	}
}
