package at.smiech.cyanbat.gameobjects

import android.graphics.Color
import android.graphics.Rect

import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.smiech.cyanbat.util.Vector2D
import at.smiech.cyanbat.screen.CyanBatBaseScreen

/**
 * Describes the of an GameObject.
 */
abstract class MovableGameObject protected constructor(override var rectangle: Rect) : GameObject {
    var removeMe = false
    protected var velocity = Vector2D()


    override fun scheduledForRemoval(): Boolean {
        return removeMe
    }

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        rectangle.left += velocity.x.toInt()
        rectangle.right += velocity.x.toInt()
        rectangle.top += velocity.y.toInt()
        rectangle.bottom += velocity.y.toInt()
        if (rectangle.right < 0 || rectangle.left - 1 > CyanBatBaseScreen.DISPLAY_HEIGHT)
            removeMe = true
    }

    override fun draw(g: Graphics) {
        if (GameObject.DEBUG) {
            g.drawRect(rectangle.left, rectangle.top, rectangle.width(), rectangle.height(),
                    Color.RED)
        }

    }
}