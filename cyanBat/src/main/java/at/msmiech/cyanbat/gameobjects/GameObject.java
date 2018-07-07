package at.msmiech.cyanbat.gameobjects;

import android.graphics.Rect;

import java.util.List;

import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Input.TouchEvent;
import at.msmiech.cyanbat.CyanBatGame;

/**
 * A common in-game-object that can be updated and drawn on screen
 *
 * @author mart1n8891
 */
public interface GameObject {
    // const
    String TAG = CyanBatGame.TAG;
    boolean DEBUG = CyanBatGame.DEBUG;

    boolean scheduledForRemoval();

    void update(float deltaTime, List<TouchEvent> touchEvents);

    void draw(Graphics g);

    Rect getRectangle();
}
