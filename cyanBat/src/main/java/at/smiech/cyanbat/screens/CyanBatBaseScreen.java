package at.smiech.cyanbat.screens;

import android.graphics.Color;
import at.grueneis.game.framework.Game;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Screen;

public class CyanBatBaseScreen extends Screen {
	public static int DISPLAY_HEIGHT = 480;
	public static int DISPLAY_WIDTH = 320;

	public static Game game;

	public CyanBatBaseScreen(Game game) {
		super(game);
		CyanBatBaseScreen.game = game;
	}

	@Override
	public void update(float deltaTime) {

	}

	@Override
	public void present(float deltaTime) {

	}

	@Override
	public void pause() {
		// TODO Auto-generated method stub

	}

	@Override
	public void resume() {
		// TODO Auto-generated method stub

	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	public void drawMap(Graphics g) {
		g.clear(Color.BLACK);

	}
}
