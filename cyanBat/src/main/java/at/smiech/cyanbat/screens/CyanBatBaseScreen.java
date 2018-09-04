package at.smiech.cyanbat.screens;

import android.graphics.Color;

import at.grueneis.game.framework.Game;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Screen;

public abstract class CyanBatBaseScreen extends Screen {
    public static int DISPLAY_HEIGHT = 480;
    public static int DISPLAY_WIDTH = 320;

    public static Game game;

    public CyanBatBaseScreen(Game game) {
        super(game);
        CyanBatBaseScreen.game = game;
    }


    public void drawMap(Graphics g) {
        g.clear(Color.BLACK);
    }


    // Default screen implementations - override if needed
    @Override
    public void update(float deltaTime) {
        // empty default method
    }

    @Override
    public void present(float deltaTime) {
        // empty default method
    }

    @Override
    public void pause() {
        // empty default method

    }

    @Override
    public void resume() {
        // empty default method

    }

    @Override
    public void dispose() {
        // empty default method

    }

}
