package at.smiech.cyanbat.gameobject

import android.graphics.Rect
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent

/**
 * A common in-game-object that can be updated and drawn on screen
 *
 * @author mart1n8891
 */
interface GameObject {
    val rectangle: Rect

    fun update(deltaTime: Float, touchEvents: List<TouchEvent>)

    fun draw(g: Graphics)

    fun isScheduledForRemoval(): Boolean
}
