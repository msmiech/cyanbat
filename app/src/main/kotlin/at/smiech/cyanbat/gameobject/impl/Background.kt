package at.smiech.cyanbat.gameobject.impl

import android.util.Log
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Pixmap
import at.grueneis.game.framework.math.Rect
import at.grueneis.game.framework.math.Vector2
import at.smiech.cyanbat.gameobject.GameObject
import at.smiech.cyanbat.gameobject.PixmapGameObject
import at.smiech.cyanbat.util.DEBUG
import at.smiech.cyanbat.util.TAG

class Background(
    x: Int,
    y: Int,
    pixmap: Pixmap,
    private val frameBufferWidth: Int,
    private val gameObjects: MutableList<GameObject>
) : PixmapGameObject(Rect.fromLTWH(x.toFloat(), y.toFloat(), pixmap.width.toFloat(), pixmap.height.toFloat()), pixmap) {

    init {
        velocity = Vector2(x = -2f)
    }

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        if (DEBUG)
            Log.d(TAG, "updateBackground")
        count += 1
        if (count < 2) {
            val bgArea = rectangle.right
            if (bgArea - 5 < frameBufferWidth) {
                gameObjects.add(
                    0,
                    Background(
                        frameBufferWidth,
                        0,
                        pixmap,
                        frameBufferWidth,
                        gameObjects
                    )
                )
            }
        }
        super.update(deltaTime, touchEvents)
    }

    override fun draw(g: Graphics) {
        if (DEBUG)
            Log.d(TAG, "drawBackground")
        g.drawPixmap(pixmap, rectangle.left.toInt(), rectangle.top.toInt())
    }

    companion object {
        var count = 0
    }
}
