package at.smiech.cyanbat.service

import android.util.Log
import at.grueneis.game.framework.math.Rect
import at.smiech.cyanbat.activity.CyanBatGameActivity
import at.smiech.cyanbat.gameobject.Collidable
import at.smiech.cyanbat.gameobject.GameObject
import at.smiech.cyanbat.gameobject.impl.Explosion
import at.smiech.cyanbat.gameobject.impl.Shot
import at.smiech.cyanbat.util.DEBUG
import at.smiech.cyanbat.util.TAG

/**
 * Collision detection implementation to be run in a separate thread on each game loop.
 */
class CollisionDetector(private val gameObjects: MutableList<GameObject>) {
    private val objectsToCheck = mutableListOf<Collidable>()

    fun checkCollisions() {
        if (DEBUG)
            Log.d(TAG, "checkCollisions")
        if (objectsToCheck.isEmpty() || objectsToCheck.size < 2)
        // at least two different objects have to be involved in a collision
            return
        for (i in objectsToCheck.indices) {
            val main = objectsToCheck[i]
            if (main !is GameObject) {
                continue
            }
            for (j in objectsToCheck.indices) {
                if (i == j) {
                    continue
                }
                val goOther = objectsToCheck[j] as GameObject
                checkCollision(main, goOther)
            }
        }
        objectsToCheck.removeIf { obj -> obj is GameObject && obj.isScheduledForRemoval() }
    }

    private fun checkCollision(main: GameObject, other: GameObject) {
        if (DEBUG)
            Log.d(
                TAG,
                "CheckCollision(GameObject) with this Object (by using rect.intersect(rect2)"
            )

        if (main !is Collidable) {
            return
        }
        if (main.isScheduledForRemoval() || other.isScheduledForRemoval()) {
            return
        }

        val mainRect = main.rectangle
        val otherRect = other.rectangle

        if (mainRect === otherRect) {
            return
        }

        val tolRect = mainRect.inflate(-COLLISION_TOLERANCE.toFloat())
        if (tolRect.intersects(otherRect)) {
            if (other is Shot) {
                if (main === other.firedBy) {
                    return
                }
            }
            val collidableMain = main as Collidable
            val collidableOther = other as Collidable
            collidableMain.hit()
            
            val explosionRect = Rect.fromLTWH(
                otherRect.left,
                otherRect.top,
                Explosion.realWidth.toFloat(),
                otherRect.height
            )
            collidableOther.hit()
            val explosionObject =
                Explosion(explosionRect, CyanBatGameActivity.gameAssets.graphics.explosion)
            gameObjects.add(explosionObject)
        }
    }

    fun addObjectToCheck(go: Collidable) {
        objectsToCheck.add(go)
    }

    companion object {
        private const val COLLISION_TOLERANCE = 5
    }
}
