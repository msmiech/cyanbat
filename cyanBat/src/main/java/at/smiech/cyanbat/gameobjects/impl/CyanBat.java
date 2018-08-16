package at.smiech.cyanbat.gameobjects.impl;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Rect;
import android.util.Log;

import at.grueneis.game.framework.Input.TouchEvent;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Pixmap;
import at.smiech.cyanbat.activities.CyanBatGameActivity;
import at.smiech.cyanbat.gameobjects.Collidable;
import at.smiech.cyanbat.gameobjects.PixmapGameObject;
import at.smiech.cyanbat.screens.CyanBatBaseScreen;
import at.smiech.cyanbat.screens.GameOverScreen;
import at.smiech.cyanbat.screens.GameScreen;

public class CyanBat extends PixmapGameObject implements Collidable {

    public final static int DEFAULT_WIDTH = 45;
    private static final float ANIM_TICK_INTERVAL = 0.2f;
    private static final float TICK_INITIAL = 0.009f;

    private float animTickTime = 0;
    public boolean alive = true;
    public int lives = 3;
    private int animTick;
    private float tickTime = 0;
    private float tick = TICK_INITIAL;
    private int srcX;
    private GameScreen gs;
    private ArrayList<CyanTrail> curvatures = new ArrayList<>();
    private static final float MAX_HIT_COOLDOWN = 0.5f;
    private float hitCooldown = MAX_HIT_COOLDOWN; // cooldown grace period in seconds

    public CyanBat(int x, int y, int width, int height, Pixmap pm, GameScreen gs) {
        super(new Rect(x, y, x + DEFAULT_WIDTH, y + height), pm);
        this.gs = gs;
    }

    @Override
    public void update(float deltaTime, List<TouchEvent> touchEvents) {
        if (DEBUG)
            Log.d(TAG, "updateBat");
        updateLogic(deltaTime, touchEvents);
        updateAnimation(deltaTime);
        updateCurvatations();

        if (CyanBatGameActivity.SHOOTING_ENABLED) {
            if (CyanBatBaseScreen.game.getInput().getTouchEvents().size() > 1)
                shoot();
        }
        super.update(deltaTime, touchEvents);
    }

    private void updateCurvatations() {
        Rect potentialRect = new Rect(rect.left,
                (rect.top + (rect.height() / 2)), rect.left + rect.width() / 4,
                rect.bottom);
        for (int i = 0; i < curvatures.size(); i++) {
            CyanTrail curv = curvatures.get(i);
            if (curv == null || curv.removeMe) {
                curvatures.remove(curv);
                continue;
            }
            if (Rect.intersects(curv.rect, potentialRect)) {
                return;
            }
        }

        potentialRect.left -= 2;
        potentialRect.right -= 1;
        CyanTrail go = new CyanTrail(potentialRect);
        curvatures.add(go);
        gs.gameObjects.add(go);
    }

    private void shoot() {
        if (Shot.count > 1)
            return;
        Shot shot = new Shot(new Rect(rect.left, rect.top,
                rect.left + CyanBatGameActivity.shot.getWidth(), rect.top
                + CyanBatGameActivity.shot.getHeight()), CyanBatGameActivity.shot, this);
        gs.gameObjects.add(shot);
        gs.colChk.addObjectToCheck(shot);
    }

    private void updateAnimation(float deltaTime) {
        animTickTime += deltaTime;
        if (animTickTime > ANIM_TICK_INTERVAL) {
            animTick = (animTick + 1) % 2;
            animTickTime -= ANIM_TICK_INTERVAL;
        }
        switch (animTick) {
            case 0:
                srcX = 0;
                break;
            case 1:
                srcX = 50;
                break;
        }
    }

    private void updateLogic(float deltaTime, List<TouchEvent> touchEvents) {
        if (alive) {
            if(hitCooldown > 0.f) {
                hitCooldown -= deltaTime;
            }
            velocity.x = 0;
            velocity.y = 0;
            if (!touchEvents.isEmpty()) {
                tickTime += deltaTime;
                while (tickTime > tick) {
                    tickTime -= tick;
                    for (int i = 0; i < touchEvents.size(); i++) {
                        TouchEvent touch = touchEvents.get(i);
                        if (touch.type == TouchEvent.TOUCH_DRAGGED)
                            move(touch);
                    }

                }
            }
        } else {
            velocity.x = 0;
            velocity.y = 2;
            if (rect.top > CyanBatBaseScreen.DISPLAY_WIDTH)
                CyanBatBaseScreen.game
                        .setScreen(new GameOverScreen(GameScreen.game));
        }
    }

    private void move(TouchEvent touch) {
        if (DEBUG)
            Log.d(TAG, "moveBat");
        if (touch.x > rect.left) {
            if (rect.right < CyanBatBaseScreen.DISPLAY_HEIGHT) {
                velocity.x = 3;
            }
        } else {
            if (rect.left > 0) {
                velocity.x = -3;
            }
        }
        if (touch.y > rect.bottom) {
            if (rect.bottom < CyanBatBaseScreen.DISPLAY_WIDTH) {
                velocity.y = 3;
            }
        } else {
            if (rect.top > 0) {
                velocity.y = -3;
            }
        }

    }

    @Override
    public void draw(Graphics g) {
        if (DEBUG)
            Log.d(TAG, "drawBat");
        g.drawPixmap(pixmap, rect.left, rect.top, srcX, 0, DEFAULT_WIDTH,
                rect.height());
        super.draw(g);
    }

    public void hit() {
        CyanBatGameActivity.vib.vibrate(250);
        if (hitCooldown <= 0.01f) {
            lives -= 1;
            hitCooldown = MAX_HIT_COOLDOWN;
        }
        if (lives < 1) {
            lives = 0;
            CyanBatGameActivity.deathSound.play(100);
            alive = false;
            gs.saveHighscore();
            CyanBatGameActivity.musicPlayer.stopMusic();
            gs.interruptThreads();
            CyanBatGameActivity.gameOverMusic.play();
        }
    }
}
