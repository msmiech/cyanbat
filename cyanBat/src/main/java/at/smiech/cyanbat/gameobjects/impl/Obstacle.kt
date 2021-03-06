package at.smiech.cyanbat.gameobjects.impl

import android.graphics.Rect
import android.util.Log
import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Pixmap
import at.smiech.cyanbat.gameobjects.Collidable
import at.smiech.cyanbat.gameobjects.GameObject
import at.smiech.cyanbat.gameobjects.PixmapGameObject

class Obstacle(x: Int, y: Int, pm: Pixmap) : PixmapGameObject(Rect(x, y, x + pm.width, y + pm.height), pm), Collidable {

    init {
        velocity.x = -1f
    }

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        if (GameObject.DEBUG)
            Log.d(GameObject.TAG, "updateObstacle")
        super.update(deltaTime, touchEvents)
    }

    override fun hit() {
        removeMe = true
    }

    override fun draw(g: Graphics) {
        g.drawPixmap(pixmap, rectangle.left, rectangle.top)
        super.draw(g)
    }

}
