package at.smiech.cyanbat.gameobjects.impl

import android.graphics.Rect
import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Pixmap
import at.smiech.cyanbat.gameobjects.PixmapGameObject

class Explosion(rect: Rect, pixmap: Pixmap) : PixmapGameObject(rect, pixmap) {

    private val ANIM_TICK_INTERVAL = 0.3f
    private var animTickTime = 0.0f
    private var animTick: Int = 0
    private var srcX: Int = 0

    init {
        velocity.x = -1f
    }

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        updateAnimation(deltaTime)
        super.update(deltaTime, touchEvents)
    }

    private fun updateAnimation(deltaTime: Float) {
        animTickTime += deltaTime
        if (animTickTime > ANIM_TICK_INTERVAL) {
            animTick = (animTick + 1) % 5
            animTickTime -= ANIM_TICK_INTERVAL
        }
        when (animTick) {
            0 -> srcX = 0
            1 -> srcX = 50
            2 -> srcX = 115
            3 -> srcX = 180
            4 -> {
                srcX = 245
                removeMe = true
            }
        }
    }

    override fun draw(g: Graphics) {
        g.drawPixmap(pixmap, rectangle.left, rectangle.top, srcX, 0, realWidth,
                rectangle.bottom)
    }

    companion object {
        val realWidth = 25
    }

}
