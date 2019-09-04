package at.smiech.cyanbat.gameobjects.impl

import android.graphics.Rect
import android.util.Log

import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Pixmap
import at.smiech.cyanbat.activities.CyanBatGameActivity
import at.smiech.cyanbat.gameobjects.GameObject
import at.smiech.cyanbat.gameobjects.PixmapGameObject
import at.smiech.cyanbat.screens.CyanBatBaseScreen

class Background(x: Int, y: Int, pm: Pixmap, gameObjects: MutableList<GameObject>) : PixmapGameObject(Rect(x, y, x + pm.width, y + pm.height), pm) {
    private val gameObjects: MutableList<GameObject> = gameObjects

    init {
        velocity.x = -2f
    }

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        if (GameObject.DEBUG)
            Log.d(GameObject.TAG, "updateBackground")
        Background.count += 1
        if (Background.count < 2) {
            val bgArea = rectangle.right
            if (bgArea - 5 < CyanBatBaseScreen.DISPLAY_HEIGHT) {
                gameObjects.add(0, Background(
                        CyanBatBaseScreen.DISPLAY_HEIGHT, 0,
                        CyanBatGameActivity.background, gameObjects))
            }
        }
        super.update(deltaTime, touchEvents)
    }

    override fun draw(g: Graphics) {
        if (GameObject.DEBUG)
            Log.d(GameObject.TAG, "drawBackground")
        g.drawPixmap(pixmap, rectangle.left, rectangle.top)
    }

    companion object {
        var count = 0
    }
}
