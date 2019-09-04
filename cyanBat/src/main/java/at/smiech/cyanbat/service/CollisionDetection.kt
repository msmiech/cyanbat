package at.smiech.cyanbat.service

import android.graphics.Rect
import android.util.Log
import at.smiech.cyanbat.activities.CyanBatGameActivity
import at.smiech.cyanbat.gameobjects.Collidable
import at.smiech.cyanbat.gameobjects.GameObject
import at.smiech.cyanbat.gameobjects.impl.Explosion
import at.smiech.cyanbat.gameobjects.impl.Shot
import java.util.*

class CollisionDetection(private val gameObjects: MutableList<GameObject>) {
    private val objectsToCheck: MutableList<Collidable>?

    init {
        this.objectsToCheck = ArrayList()
    }

    fun checkCollisions() {
        if (DEBUG)
            Log.d(TAG, "checkCollisions")
        synchronized(gameObjects) {
            if (objectsToCheck == null || objectsToCheck.isEmpty() || objectsToCheck.size < 2)
            // at least two different objects have to be involved in a collision
                return
            for (i in objectsToCheck!!.indices) {
                val main = objectsToCheck[i]
                if (main !is GameObject) {
                    continue
                }
                val goMain = main as GameObject
                for (j in objectsToCheck.indices) {
                    val other = objectsToCheck[j]
                    if (other === main)
                        continue
                    if (other == null || other !is GameObject) {
                        continue
                    }
                    val goOther = other as GameObject
                    checkCollision(goMain, goOther)
                }
            }
            objectsToCheck.filter { obj -> obj is GameObject && obj.scheduledForRemoval() }.forEach { objectsToCheck.remove(it) }
        }
    }

    private fun checkCollision(main: GameObject, other: GameObject) {
        if (DEBUG)
            Log.d(TAG,
                    "(CheckCollision(GameObject) with this Object (by using rect.intersect(rect2))")

        val mainRect = main.rectangle
        val otherRect = other.rectangle

        if (mainRect == null || otherRect == null || mainRect === otherRect) {
            return
        }

        val tolRect = Rect(mainRect.left + COLLISION_TOLERANCE,
                mainRect.top + COLLISION_TOLERANCE, mainRect.right - 2 * COLLISION_TOLERANCE, mainRect.bottom - 2 * COLLISION_TOLERANCE)
        if (Rect.intersects(tolRect, otherRect)) {
            if (other is Shot) {
                if (main === other.firedByObject)
                    return
            }
            if (main !is Collidable) {
                return
            }
            if (main.scheduledForRemoval() || other.scheduledForRemoval()) {
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
        objectsToCheck!!.add(go)
    }

    @Synchronized
    fun removeObjectToCheck(go: Collidable?) {
        if (go == null)
            throw IllegalArgumentException("Passed game object to remove from collision detection was null!")
        objectsToCheck!!.remove(go)
    }

    companion object {

        private val COLLISION_TOLERANCE = 5
        private val DEBUG = CyanBatGameActivity.DEBUG
        private val TAG = CyanBatGameActivity.TAG
    }
}
