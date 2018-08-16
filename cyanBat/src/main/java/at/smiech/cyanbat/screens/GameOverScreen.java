package at.smiech.cyanbat.screens;

import android.content.Intent;

import java.util.List;

import at.grueneis.game.framework.Game;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Input.TouchEvent;
import at.smiech.cyanbat.activities.CyanBatGameActivity;
import at.smiech.cyanbat.activities.MainMenuActivity;

public class GameOverScreen extends CyanBatBaseScreen {

    private List<TouchEvent> touchEvents;
    private Graphics g;

    public GameOverScreen(Game game) {
        super(game);
        init();

    }

    private void init() {
        g = game.getGraphics();
        System.gc();
        initSounds();
    }

    private void initSounds() {
        if (CyanBatGameActivity.gameTrack.isPlaying()) {
            CyanBatGameActivity.gameTrack.stop();
            CyanBatGameActivity.gameTrack.setLooping(false);
        }
    }

    @Override
    public void update(float deltaTime) {
        touchEvents = game.getInput().getTouchEvents();
        for (TouchEvent event : touchEvents) {
            if (event.type == TouchEvent.TOUCH_UP) {
                CyanBatGameActivity.vib.vibrate(250);
                //game.setScreen(new StartScreen(game));
                Intent mainMenuIntent = new Intent(game.getContext(), MainMenuActivity.class);
                game.getContext().startActivity(mainMenuIntent);
            }
        }
        super.update(deltaTime);
    }

    @Override
    public void present(float deltaTime) {
        drawMap(g);
        g.drawPixmap(CyanBatGameActivity.gameOver, 0, 0);
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
