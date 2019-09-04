package at.smiech.cyanbat.gameobjects

import android.graphics.Rect
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Pixmap

abstract class PixmapGameObject protected constructor(rect: Rect, var pixmap: Pixmap) : MovableGameObject(rect) {

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        super.update(deltaTime, touchEvents)
    }

    override fun draw(g: Graphics) {
        super.draw(g)
    }
}
