package at.smiech.cyanbat.gameobjects

import android.graphics.Rect

import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.smiech.cyanbat.activities.CyanBatGameActivity

/**
 * A common in-game-object that can be updated and drawn on screen
 *
 * @author mart1n8891
 */
interface GameObject {

    val rectangle: Rect

    fun scheduledForRemoval(): Boolean

    fun update(deltaTime: Float, touchEvents: List<TouchEvent>)

    fun draw(g: Graphics)

    companion object {
        // const
        val TAG = CyanBatGameActivity.TAG
        val DEBUG = CyanBatGameActivity.DEBUG
    }
}
