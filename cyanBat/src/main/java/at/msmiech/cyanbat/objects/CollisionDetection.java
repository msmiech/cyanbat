package at.msmiech.cyanbat.objects;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Rect;
import android.util.Log;

import at.msmiech.cyanbat.CyanBatGame;
import at.msmiech.cyanbat.interfaces.Collidable;
import at.msmiech.cyanbat.interfaces.GameObject;
import at.msmiech.cyanbat.objects.gameobjects.AbstractGameObject;
import at.msmiech.cyanbat.objects.gameobjects.Explosion;
import at.msmiech.cyanbat.objects.gameobjects.Shot;

public class CollisionDetection {

    private static final int COLLISION_TOLERANCE = 5;
    private static final boolean DEBUG = CyanBatGame.DEBUG;
    private static final String TAG = CyanBatGame.TAG;
    private final List<GameObject> gameObjects;
    private List<Collidable> objectsToCheck;
    private Rect tolRect; // tolerance rectangle

    public CollisionDetection(List<GameObject> gameObjects) {
        this.gameObjects = gameObjects;
        this.objectsToCheck = new ArrayList<>();
    }

    public void checkCollisions() {
        if (DEBUG)
            Log.d(TAG, "checkCollisions");
        synchronized (gameObjects) {
            if (objectsToCheck == null || objectsToCheck.isEmpty() || objectsToCheck.size() < 2) // at least two objects must be involved in a collision
                return;
            for (int i = 0; i < objectsToCheck.size(); i++) {
                AbstractGameObject main = (AbstractGameObject) gameObjects
                        .get(i);
                if (main.removeMe)
                    removeObjectToCheck(main);
                for (int j = 0; j < gameObjects.size(); j++) {
                    AbstractGameObject other = (AbstractGameObject) gameObjects
                            .get(j);
                    if (other == main)
                        continue;
                    if (other instanceof Collidable) {
                        checkCollision(main, other);
                    }
                }
            }
        }
    }

    private void checkCollision(AbstractGameObject main, AbstractGameObject go) {
        if (DEBUG)
            Log.d(TAG,
                    "(CheckCollision(GameObject) with this Object (by using rect.intersect(rect2))");

        tolRect = new Rect(main.rect.left + COLLISION_TOLERANCE,
                main.rect.top + COLLISION_TOLERANCE, main.rect.right - 2
                * COLLISION_TOLERANCE, main.rect.bottom - 2
                * COLLISION_TOLERANCE);
        if (Rect.intersects(tolRect, go.rect)) {
            if (go instanceof Shot) {
                Shot shot = (Shot) go;
                if (main == shot.firedByObject)
                    return;
            }
            if (!(main instanceof Collidable))
                return;
            ((Collidable) main).hit();
            Rect explosionRect = go.rect;
            explosionRect.right = explosionRect.left + Explosion.realWidth;
            ((Collidable) go).hit();
            go = new Explosion(explosionRect, CyanBatGame.explosion);
            gameObjects.add(go);
        }
        tolRect = null;
    }

    public synchronized void addObjectToCheck(AbstractGameObject go) {
        if (go instanceof Collidable)
            objectsToCheck.add((Collidable) go);
    }

    public synchronized void removeObjectToCheck(AbstractGameObject go) {
        if (!(go instanceof Collidable))
            return;
        go.removeMe = true;
        objectsToCheck.remove(go);
    }
}
