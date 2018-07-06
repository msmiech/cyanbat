package at.msmiech.cyanbat.objects.gameobjects;

import java.util.List;

import android.graphics.Color;
import android.graphics.Rect;

import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Input.TouchEvent;
import at.msmiech.cyanbat.interfaces.GameObject;
import at.msmiech.cyanbat.objects.Vector2D;
import at.msmiech.cyanbat.screens.CyanBatBaseScreen;

public abstract class AbstractGameObject implements GameObject {
    public Rect rect;
    public boolean removeMe = false;
    public Vector2D velocity = new Vector2D();

    // Describes the
    // movement of an GameObject.

    protected AbstractGameObject(Rect rect) {
        this.rect = rect;
    }


    @Override
    public boolean scheduledForRemoval() {
        return removeMe;
    }

    public void update(float deltaTime, List<TouchEvent> touchEvents) {
        rect.left += velocity.x;
        rect.right += velocity.x;
        rect.top += velocity.y;
        rect.bottom += velocity.y;
        if (rect.right < 0 || rect.left - 1 > CyanBatBaseScreen.DISPLAY_HEIGHT)
            removeMe = true;
    }

    public void draw(Graphics g) {
        if (DEBUG) {
            g.drawRect(rect.left, rect.top, rect.width(), rect.height(),
                    Color.RED);
        }

    }
}