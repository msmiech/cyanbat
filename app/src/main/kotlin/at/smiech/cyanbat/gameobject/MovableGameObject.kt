package at.smiech.cyanbat.gameobject

import android.graphics.Color
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.math.Rect
import at.grueneis.game.framework.math.Vector2
import at.smiech.cyanbat.util.DEBUG

/**
 * Base class for objects that move in the game world.
 */
abstract class MovableGameObject protected constructor(initialRect: Rect) : GameObject {
    override var rectangle: Rect = initialRect
        protected set

    protected var velocity = Vector2.Zero

    var removeMe = false
    override fun isScheduledForRemoval() = removeMe

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        // Move with float precision directly using the immutable Rect
        rectangle = rectangle.offset(velocity.x, velocity.y)

        // if the game object is out of bounds, schedule it for removal
        if (rectangle.right < 0) {
            removeMe = true
        }
    }

    /**
     * Teleports the object to a new position.
     */
    protected fun moveTo(x: Float, y: Float) {
        rectangle = Rect.fromLTWH(x, y, rectangle.width, rectangle.height)
    }

    override fun draw(g: Graphics) {
        if (DEBUG) {
            // draw a red bounding rectangle around each game object
            // snap to pixels only for rendering
            g.drawRect(
                rectangle.left.toInt(),
                rectangle.top.toInt(),
                rectangle.width.toInt(),
                rectangle.height.toInt(),
                Color.RED
            )
        }
    }
}
