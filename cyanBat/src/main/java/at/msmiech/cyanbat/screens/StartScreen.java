package at.msmiech.cyanbat.screens;

import java.util.List;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import at.grueneis.game.framework.Game;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Input.TouchEvent;
import at.msmiech.cyanbat.activities.CyanBatGameActivity;

public class StartScreen extends CyanBatBaseScreen {

	private Graphics g;

	public StartScreen(Game game) {
		super(game);
		init();
	}

	private void init() {
		g = game.getGraphics();
		System.gc();
		if (!CyanBatGameActivity.menuTheme.isLooping()) {
			CyanBatGameActivity.menuTheme.setLooping(true);
			CyanBatGameActivity.menuTheme.play();
		}
	}

	@Override
	public void update(float deltaTime) {
		List<TouchEvent> touchEvents = game.getInput().getTouchEvents();
		for (TouchEvent event : touchEvents) {
			if (event.type == TouchEvent.TOUCH_UP) {
				if (event.x > 50 && event.x < 190 && event.y < 150
						&& event.y > 90) {
					CyanBatGameActivity.vib.vibrate(250);
					game.setScreen(new GameScreen(game));
				}
				if (event.x > 20 && event.x < 210 && event.y < 50
						&& event.y > 25) {
					CyanBatGameActivity.vib.vibrate(200);
					System.exit(0);

				}

			}
		}
		super.update(deltaTime);
	}

	@Override
	public void present(float deltaTime) {

		drawMap(g);
		g.drawPixmap(CyanBatGameActivity.mainMenu, -2, -2);
		g.drawPixmap(CyanBatGameActivity.menuButtons, 5, 5);
		Context context = game.getContext();
		try {
			g.drawString(
					"Version: " + context.getPackageManager().getPackageInfo(
							context.getPackageName(), 0).versionName, 5, 310, 15,
					Color.RED);
		} catch (NameNotFoundException e) {
			g.drawString(
					"NaN", 5, 310, 15,
					Color.RED);
		}
	}

	@Override
	public void pause() {
		if (CyanBatGameActivity.menuTheme.isPlaying())
			CyanBatGameActivity.menuTheme.stop();
		CyanBatGameActivity.menuTheme.setLooping(false);
		super.pause();
	}

	@Override
	public void resume() {
		if (!CyanBatGameActivity.menuTheme.isPlaying())
			CyanBatGameActivity.menuTheme.play();
		CyanBatGameActivity.menuTheme.setLooping(true);
		super.resume();
	}
}
