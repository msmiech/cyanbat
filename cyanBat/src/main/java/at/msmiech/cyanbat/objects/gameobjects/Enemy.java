package at.msmiech.cyanbat.objects.gameobjects;

import java.util.List;
import java.util.Random;

import android.graphics.Rect;
import android.util.Log;

import at.grueneis.game.framework.Input.TouchEvent;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Pixmap;
import at.msmiech.cyanbat.interfaces.Collidable;

public class Enemy extends PixmapGameObject implements Collidable {

    public int type = 0;
    private float ANIM_TICK_INTERVAL = 0.2f;
    private float animTickTime = 0;
    private int srcX;
    private int animTick;
    private static Random rnd = new Random();
    public static final int realWidth = 28;

    public Enemy(int x, int y, int width, int height, Pixmap pm, int type) {
        super(new Rect(x, y, x + realWidth, y + height), pm);
        this.type = type;
        velocity.x = -1;
        velocity.y = 2;
    }

    @Override
    public void update(float deltaTime, List<TouchEvent> touchEvents) {
        updateAnimation(deltaTime);
        if (rnd.nextBoolean()) {
            velocity.y *= (-1);
        }
        super.update(deltaTime, touchEvents);
    }

    private void updateAnimation(float deltaTime) {
        animTickTime += deltaTime;
        if (animTickTime > ANIM_TICK_INTERVAL) {
            animTick = (animTick + 1) % 2;
            animTickTime -= ANIM_TICK_INTERVAL;
        }
    }

    @Override
    public void draw(Graphics g) {
        if (DEBUG)
            Log.d(TAG, "drawEnemy");
        switch (type) {
            case 0:
                switch (animTick) {
                    case 0:
                        srcX = 0;
                        break;
                    case 1:
                        srcX = 32;
                        break;
                }
                break;
            case 1:
                switch (animTick) {
                    case 0:
                        srcX = 67;
                        break;
                    case 1:
                        srcX = 102;
                        break;
                }
                break;
            case 2:
                switch (animTick) {
                    case 0:
                        srcX = 137;
                        break;
                    case 1:
                        srcX = 173;
                        break;
                }
                break;
        }
        g.drawPixmap(pixmap, rect.left, rect.top, srcX, 0, realWidth,
                rect.height());
        super.draw(g);
    }

    @Override
    public void hit() {
        removeMe = true;
    }

}
