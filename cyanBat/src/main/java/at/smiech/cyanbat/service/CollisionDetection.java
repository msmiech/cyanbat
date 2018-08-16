package at.smiech.cyanbat.service;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Rect;
import android.util.Log;

import at.smiech.cyanbat.activities.CyanBatGameActivity;
import at.smiech.cyanbat.gameobjects.Collidable;
import at.smiech.cyanbat.gameobjects.GameObject;
import at.smiech.cyanbat.gameobjects.impl.Explosion;
import at.smiech.cyanbat.gameobjects.impl.Shot;

public class CollisionDetection {

    private static final int COLLISION_TOLERANCE = 5;
    private static final boolean DEBUG = CyanBatGameActivity.DEBUG;
    private static final String TAG = CyanBatGameActivity.TAG;
    private final List<GameObject> gameObjects;
    private List<Collidable> objectsToCheck;

    public CollisionDetection(List<GameObject> gameObjects) {
        this.gameObjects = gameObjects;
        this.objectsToCheck = new ArrayList<>();
    }

    public void checkCollisions() {
        if (DEBUG)
            Log.d(TAG, "checkCollisions");
        synchronized (gameObjects) {
            if (objectsToCheck == null || objectsToCheck.isEmpty() || objectsToCheck.size() < 2) // at least two different objects have to be involved in a collision
                return;
            for (int i = 0; i < objectsToCheck.size(); i++) {
                Collidable main = objectsToCheck.get(i);
                if (main == null || !(main instanceof GameObject)) {
                    continue;
                }
                GameObject goMain = (GameObject) main;
                if (goMain.scheduledForRemoval()) {
                    removeObjectToCheck(main);
                    continue;
                }
                for (int j = 0; j < objectsToCheck.size(); j++) {
                    Collidable other = objectsToCheck.get(j);
                    if (other == main)
                        continue;
                    if (other == null || !(other instanceof GameObject)) {
                        continue;
                    }
                    GameObject goOther = (GameObject) other;
                    if (goOther.scheduledForRemoval()) {
                        removeObjectToCheck(other);
                        continue;
                    }
                    checkCollision(goMain, goOther);
                }
            }
        }
    }

    private void checkCollision(GameObject main, GameObject other) {
        if (DEBUG)
            Log.d(TAG,
                    "(CheckCollision(GameObject) with this Object (by using rect.intersect(rect2))");

        Rect mainRect = main.getRectangle();
        Rect otherRect = other.getRectangle();

        if (mainRect == null || otherRect == null || mainRect == otherRect) {
            return;
        }

        Rect tolRect = new Rect(mainRect.left + COLLISION_TOLERANCE,
                mainRect.top + COLLISION_TOLERANCE, mainRect.right - 2
                * COLLISION_TOLERANCE, mainRect.bottom - 2
                * COLLISION_TOLERANCE);
        if (Rect.intersects(tolRect, otherRect)) {
            if (other instanceof Shot) {
                Shot shot = (Shot) other;
                if (main == shot.firedByObject)
                    return;
            }
            if (!(main instanceof Collidable)) {
                return;
            }
            if (main.scheduledForRemoval() || other.scheduledForRemoval()) {
                return;
            }
            Collidable collidableMain = (Collidable) main;
            Collidable collidableOther = (Collidable) other;
            collidableMain.hit();
            Rect explosionRect = new Rect(otherRect); // copy the rectangle
            explosionRect.right = explosionRect.left + Explosion.realWidth;
            collidableOther.hit();
            GameObject explosionObject = new Explosion(explosionRect, CyanBatGameActivity.explosion);
            gameObjects.add(explosionObject);
        }
    }

    public synchronized void addObjectToCheck(Collidable go) {
        objectsToCheck.add(go);
    }

    public synchronized void removeObjectToCheck(Collidable go) {
        if (go == null)
            throw new IllegalArgumentException("Passed game object to remove from collision detection was null!");
        objectsToCheck.remove(go);
    }
}
