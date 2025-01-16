package at.smiech.cyanbat.gameobject

import android.graphics.Color
import android.graphics.Rect

import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.smiech.cyanbat.util.Vector2D
import at.smiech.cyanbat.util.DEBUG

/**
 * Describes the of an GameObject.
 */
abstract class MovableGameObject protected constructor(override var rectangle: Rect) : GameObject {
    protected var velocity = Vector2D()

    var removeMe = false
    override fun isScheduledForRemoval() = removeMe

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        rectangle.left += velocity.x.toInt()
        rectangle.right += velocity.x.toInt()
        rectangle.top += velocity.y.toInt()
        rectangle.bottom += velocity.y.toInt()
        // if the game object is out of bounds, schedule it for removal
        if (rectangle.right < 0) {
            removeMe = true
        }
    }

    override fun draw(g: Graphics) {
        if (DEBUG) {
            // draw a red bounding rectangle around each game object
            g.drawRect(
                rectangle.left, rectangle.top, rectangle.width(), rectangle.height(),
                Color.RED
            )
        }

    }
}