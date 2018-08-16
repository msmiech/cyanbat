package at.smiech.cyanbat.gameobjects;

import java.util.List;

import android.graphics.Color;
import android.graphics.Rect;

import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Input.TouchEvent;
import at.smiech.cyanbat.util.Vector2D;
import at.smiech.cyanbat.screens.CyanBatBaseScreen;

/**
 * Describes the of an GameObject.
 */
public abstract class MovableGameObject implements GameObject {
    public Rect rect;
    public boolean removeMe = false;
    protected Vector2D velocity = new Vector2D();

    protected MovableGameObject(Rect rect) {
        this.rect = rect;
    }


    @Override
    public boolean scheduledForRemoval() {
        return removeMe;
    }

    @Override
    public Rect getRectangle() {
        return rect;
    }

    @Override
    public void update(float deltaTime, List<TouchEvent> touchEvents) {
        rect.left += velocity.x;
        rect.right += velocity.x;
        rect.top += velocity.y;
        rect.bottom += velocity.y;
        if (rect.right < 0 || rect.left - 1 > CyanBatBaseScreen.DISPLAY_HEIGHT)
            removeMe = true;
    }

    @Override
    public void draw(Graphics g) {
        if (DEBUG) {
            g.drawRect(rect.left, rect.top, rect.width(), rect.height(),
                    Color.RED);
        }

    }
}