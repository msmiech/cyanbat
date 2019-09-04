package at.smiech.cyanbat.gameobjects.impl

import java.util.Random

import android.graphics.Rect
import android.util.Log

import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Pixmap
import at.smiech.cyanbat.gameobjects.Collidable
import at.smiech.cyanbat.gameobjects.GameObject
import at.smiech.cyanbat.gameobjects.PixmapGameObject

class Enemy(x: Int, y: Int, width: Int, height: Int, pm: Pixmap, type: Int) : PixmapGameObject(Rect(x, y, x + realWidth, y + height), pm), Collidable {

    var type = 0
    private val ANIM_TICK_INTERVAL = 0.2f
    private var animTickTime = 0f
    private var srcX: Int = 0
    private var animTick: Int = 0

    init {
        this.type = type
        velocity.x = -1f
        velocity.y = 2f
    }

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        updateAnimation(deltaTime)
        if (rnd.nextBoolean()) {
            velocity.y *= (-1).toFloat()
        }
        super.update(deltaTime, touchEvents)
    }

    private fun updateAnimation(deltaTime: Float) {
        animTickTime += deltaTime
        if (animTickTime > ANIM_TICK_INTERVAL) {
            animTick = (animTick + 1) % 2
            animTickTime -= ANIM_TICK_INTERVAL
        }
    }

    override fun draw(g: Graphics) {
        if (GameObject.DEBUG)
            Log.d(GameObject.TAG, "drawEnemy")
        when (type) {
            0 -> when (animTick) {
                0 -> srcX = 0
                1 -> srcX = 32
            }
            1 -> when (animTick) {
                0 -> srcX = 67
                1 -> srcX = 102
            }
            2 -> when (animTick) {
                0 -> srcX = 137
                1 -> srcX = 173
            }
        }
        g.drawPixmap(pixmap, rectangle.left, rectangle.top, srcX, 0, realWidth,
                rectangle.height())
        super.draw(g)
    }

    override fun hit() {
        removeMe = true
    }

    companion object {
        private val rnd = Random()
        val realWidth = 28
    }

}
