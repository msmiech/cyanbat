package at.smiech.cyanbat.gameobject.impl

import android.graphics.Rect
import android.util.Log

import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Pixmap
import at.smiech.cyanbat.activity.CyanBatGameActivity
import at.smiech.cyanbat.gameobject.GameObject
import at.smiech.cyanbat.gameobject.PixmapGameObject
import at.smiech.cyanbat.ui.CyanBatBaseScreen
import at.smiech.cyanbat.util.DEBUG
import at.smiech.cyanbat.util.TAG

class Background(x: Int, y: Int, pm: Pixmap, private val gameObjects: MutableList<GameObject>) : PixmapGameObject(Rect(x, y, x + pm.width, y + pm.height), pm) {

    init {
        velocity.x = -2f
    }

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        if (DEBUG)
            Log.d(TAG, "updateBackground")
        count += 1
        if (count < 2) {
            val bgArea = rectangle.right
            if (bgArea - 5 < CyanBatBaseScreen.DISPLAY_HEIGHT) {
                gameObjects.add(0, Background(
                        CyanBatBaseScreen.DISPLAY_HEIGHT, 0,
                        CyanBatGameActivity.gameAssets.graphics.background, gameObjects))
            }
        }
        super.update(deltaTime, touchEvents)
    }

    override fun draw(g: Graphics) {
        if (DEBUG)
            Log.d(TAG, "drawBackground")
        g.drawPixmap(pixmap, rectangle.left, rectangle.top)
    }

    companion object {
        var count = 0
    }
}
