package at.smiech.cyanbat.gameobject.impl

import android.util.Log
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Pixmap
import at.grueneis.game.framework.math.Rect
import at.grueneis.game.framework.math.Vector2
import at.smiech.cyanbat.gameobject.Collidable
import at.smiech.cyanbat.gameobject.PixmapGameObject
import at.smiech.cyanbat.util.DEBUG
import at.smiech.cyanbat.util.TAG
import java.util.Random
import kotlin.math.sin

class Enemy(x: Int, y: Int, width: Int, height: Int, pm: Pixmap, type: Int) :
    PixmapGameObject(Rect.fromLTWH(x.toFloat(), y.toFloat(), realWidth.toFloat(), height.toFloat()), pm), Collidable {

    var type = 0
    private val ANIM_TICK_INTERVAL = 0.2f
    private var animTickTime = 0f
    private var srcX: Int = 0
    private var animTick: Int = 0

    // Smooth movement state
    private var elapsedTime = 0f
    private val initialY = y.toFloat()
    private var verticalDirection = 1f
    private var nextDirectionChange = 0.5f + rnd.nextFloat()

    init {
        this.type = type
        // Base horizontal speed depends on type
        val speedX = when (type) {
            0 -> -2.5f // Fast scout
            1 -> -1.5f // Sine waver
            2 -> -1.2f // Zig-zagger
            else -> -1.0f
        }
        velocity = Vector2(x = speedX, y = 0f)
    }

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        elapsedTime += deltaTime
        updateAnimation(deltaTime)
        updateMovement()
        super.update(deltaTime, touchEvents)
    }

    private fun updateMovement() {
        when (type) {
            0 -> {
                // Type 0: Straight and fast, maybe a tiny bit of drift
                velocity = velocity.copy(y = sin(elapsedTime * 2f) * 0.2f)
            }
            1 -> {
                // Type 1: Sine wave oscillation
                val amplitude = 80f
                val frequency = 3f
                val targetY = initialY + sin(elapsedTime * frequency) * amplitude
                // Update velocity to steer towards targetY
                val dy = (targetY - rectangle.top) * 0.1f
                velocity = velocity.copy(y = dy)
            }
            2 -> {
                // Type 2: Zig-Zag
                if (elapsedTime > nextDirectionChange) {
                    verticalDirection *= -1f
                    nextDirectionChange = elapsedTime + 0.8f + rnd.nextFloat()
                }
                velocity = velocity.copy(y = verticalDirection * 2.5f)
            }
        }
    }

    private fun updateAnimation(deltaTime: Float) {
        animTickTime += deltaTime
        if (animTickTime > ANIM_TICK_INTERVAL) {
            animTick = (animTick + 1) % 2
            animTickTime -= ANIM_TICK_INTERVAL
        }
    }

    override fun draw(g: Graphics) {
        if (DEBUG)
            Log.d(TAG, "drawEnemy")
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
        g.drawPixmap(
            pixmap, 
            rectangle.left.toInt(), 
            rectangle.top.toInt(), 
            srcX, 0, realWidth,
            rectangle.height.toInt()
        )
        super.draw(g)
    }

    override fun hit() {
        removeMe = true
    }

    companion object {
        private val rnd = Random()
        const val realWidth = 28
    }

}
