package at.smiech.cyanbat.gameobject.impl

import android.graphics.Rect

import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Pixmap
import at.smiech.cyanbat.gameobject.Collidable
import at.smiech.cyanbat.gameobject.GameObject
import at.smiech.cyanbat.gameobject.PixmapGameObject

/**
 * TODO implement shots to be fired at enemies
 */
class Shot(
    rect: Rect,
    pixmap: Pixmap,
    var firedBy: GameObject
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
