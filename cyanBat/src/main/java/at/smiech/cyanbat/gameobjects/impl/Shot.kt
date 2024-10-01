package at.smiech.cyanbat.gameobjects.impl

import android.graphics.Rect

import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Pixmap
import at.smiech.cyanbat.gameobjects.Collidable
import at.smiech.cyanbat.gameobjects.GameObject
import at.smiech.cyanbat.gameobjects.PixmapGameObject

/**
 * TODO implement shots to be fired at enemies
 */
class Shot(
    rect: Rect, pixmap: Pixmap, var firedByObject: GameObject
) : PixmapGameObject(rect, pixmap), Collidable {

    init {
        count += 1
        velocity.x = 2f
    }

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        super.update(deltaTime, touchEvents)
        TODO()
    }

    override fun hit() {
        removeMe = true
    }

    companion object {
        var count = 0
    }
}
