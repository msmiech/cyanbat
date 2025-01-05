package at.smiech.cyanbat.service

import android.graphics.Rect
import android.util.Log
import at.smiech.cyanbat.activities.CyanBatGameActivity
import at.smiech.cyanbat.gameobjects.Collidable
import at.smiech.cyanbat.gameobjects.GameObject
import at.smiech.cyanbat.gameobjects.impl.Explosion
import at.smiech.cyanbat.gameobjects.impl.Shot

/**
 * Collision detection implementation to be run in a separate thread on each game loop.
 */
class CollisionDetector(private val gameObjects: MutableList<GameObject>) {
    private val objectsToCheck = mutableListOf<Collidable>()

    fun checkCollisions() {
        if (DEBUG)
            Log.d(TAG, "checkCollisions")
        synchronized(gameObjects) {
            if (objectsToCheck.isEmpty() || objectsToCheck.size < 2)
            // at least two different objects have to be involved in a collision
                return
            for (i in objectsToCheck.indices) {
                val main = objectsToCheck[i]
                if (main !is GameObject) {
                    continue
                }
                val goMain = main as GameObject
                for (j in objectsToCheck.indices) {
                    if (i == j) {
                        continue
                    }
                    val goOther = objectsToCheck[j] as GameObject
                    checkCollision(goMain, goOther)
                }
            }
            objectsToCheck.removeIf { obj -> obj is GameObject && obj.scheduledForRemoval() }
        }
    }

    private fun checkCollision(main: GameObject, other: GameObject) {
        if (DEBUG)
            Log.d(
                TAG,
                "(CheckCollision(GameObject) with this Object (by using rect.intersect(rect2))"
            )

        if (main !is Collidable) {
            return
        }
        if (main.scheduledForRemoval() || other.scheduledForRemoval()) {
            return
        }

        val mainRect = main.rectangle
        val otherRect = other.rectangle

        if (mainRect === otherRect) {
            return
        }

        val tolRect = Rect(
            mainRect.left + COLLISION_TOLERANCE,
            mainRect.top + COLLISION_TOLERANCE,
            mainRect.right - 2 * COLLISION_TOLERANCE,
            mainRect.bottom - 2 * COLLISION_TOLERANCE
        )
        if (Rect.intersects(tolRect, otherRect)) {
            if (other is Shot) {
                if (main === other.firedByObject)
                    return
            }
            val collidableMain = main as Collidable
            val collidableOther = other as Collidable
            collidableMain.hit()
            val explosionRect = Rect(otherRect) // copy the rectangle
            explosionRect.right = explosionRect.left + Explosion.realWidth
            collidableOther.hit()
            val explosionObject = Explosion(explosionRect, CyanBatGameActivity.explosion)
            gameObjects.add(explosionObject)
        }
    }

    @Synchronized
    fun addObjectToCheck(go: Collidable) {
        objectsToCheck.add(go)
    }

    companion object {
        private const val COLLISION_TOLERANCE = 5
        private const val DEBUG = CyanBatGameActivity.DEBUG
        private const val TAG = CyanBatGameActivity.TAG
    }
}
