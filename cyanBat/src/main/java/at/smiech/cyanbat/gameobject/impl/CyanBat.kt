package at.smiech.cyanbat.gameobject.impl

import android.graphics.Rect
import android.os.VibrationEffect
import android.os.VibrationEffect.DEFAULT_AMPLITUDE
import android.util.Log
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Pixmap
import at.smiech.cyanbat.activity.CyanBatGameActivity
import at.smiech.cyanbat.gameobject.Collidable
import at.smiech.cyanbat.gameobject.PixmapGameObject
import at.smiech.cyanbat.ui.game.GameOverScreen
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.cyanbat.util.DEBUG
import at.smiech.cyanbat.util.TAG
import java.util.*

class CyanBat(
    x: Int,
    y: Int,
    width: Int = DEFAULT_WIDTH,
    height: Int,
    pixmap: Pixmap,
    private val frameBufferWidth: Int,
    private val frameBufferHeight: Int,
    private val gameScreen: GameScreen
) : PixmapGameObject(Rect(x, y, x + width, y + height), pixmap), Collidable {

    private var animTickTime = 0f
    var alive = true
    var lives = 3
    private var animTick: Int = 0
    private var tickTime = 0f
    private val tick = TICK_INITIAL
    private var srcX: Int = 0
    private val trails = ArrayList<CyanTrail>()
    private var hitCooldown = MAX_HIT_COOLDOWN // cooldown grace period in seconds

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        if (DEBUG)
            Log.d(TAG, "updateBat")
        updateLogic(deltaTime, touchEvents)
        updateAnimation(deltaTime)
        updateTrails()

        super.update(deltaTime, touchEvents)
    }

    private fun updateTrails() {
        val potentialRect = Rect(
            rectangle.left,
            rectangle.top + rectangle.height() / 2, rectangle.left + rectangle.width() / 4,
            rectangle.bottom
        )
        if (trails.any { Rect.intersects(it.rectangle, potentialRect) }) {
            return
        }

        trails.removeIf { curve -> curve.removeMe }

        potentialRect.left -= 2
        potentialRect.right -= 1
        val go = CyanTrail(potentialRect)
        trails.add(go)
        gameScreen.gameObjects.add(go)
    }

    private fun shoot() {
        if (Shot.count > 1)
            return
        val shot = Shot(
            Rect(
                rectangle.left,
                rectangle.top,
                rectangle.left + CyanBatGameActivity.gameAssets.graphics.shot.width,
                rectangle.top + CyanBatGameActivity.gameAssets.graphics.shot.height
            ), CyanBatGameActivity.gameAssets.graphics.shot, this
        )
        gameScreen.gameObjects.add(shot)
        gameScreen.colChk.addObjectToCheck(shot)
    }

    private fun updateAnimation(deltaTime: Float) {
        animTickTime += deltaTime
        if (animTickTime > ANIM_TICK_INTERVAL) {
            animTick = (animTick + 1) % 2
            animTickTime -= ANIM_TICK_INTERVAL
        }
        when (animTick) {
            0 -> srcX = 0
            1 -> srcX = 50
        }
    }

    private fun updateLogic(deltaTime: Float, touchEvents: List<TouchEvent>) {
        if (alive) {
            if (hitCooldown > 0f) {
                hitCooldown -= deltaTime
            }
            velocity.x = 0f
            velocity.y = 0f
            if (touchEvents.isNotEmpty()) {
                tickTime += deltaTime
                while (tickTime > tick) {
                    tickTime -= tick
                    for (i in touchEvents.indices) {
                        val touch = touchEvents[i]
                        if (touch.type == TouchEvent.TOUCH_DRAGGED)
                            move(touch)
                    }

                }
            }
        } else {
            velocity.x = 0f
            velocity.y = 2f
            // check whether the bat animation has finished (i.e. bat is outside the screen)
            if (rectangle.top > frameBufferWidth) {
                // switch to the GameOverScreen, if so
                gameScreen.game.setScreen(GameOverScreen(gameScreen.game))
            }
        }
    }

    private fun move(touch: TouchEvent) {
        if (DEBUG)
        {
            Log.d(TAG, "moveBat")
        }
        if (touch.x > rectangle.centerX()) {
            if (rectangle.right < frameBufferWidth) {
                velocity.x = 3f
            }
        } else {
            if (rectangle.left > 0) {
                velocity.x = -3f
            }
        }
        if (touch.y > rectangle.centerY()) {
            if (rectangle.bottom < frameBufferHeight) {
                velocity.y = 3f
            }
        } else {
            if (rectangle.top > 0) {
                velocity.y = -3f
            }
        }

    }

    override fun draw(g: Graphics) {
        if (DEBUG)
            Log.d(TAG, "drawBat")
        g.drawPixmap(
            pixmap, rectangle.left, rectangle.top, srcX, 0, DEFAULT_WIDTH,
            rectangle.height()
        )
        super.draw(g)
    }

    override fun hit() {
        CyanBatGameActivity.gameAssets.vib.vibrate(VibrationEffect.createOneShot(250, DEFAULT_AMPLITUDE))
        if (hitCooldown <= 0.01f) {
            lives -= 1
            hitCooldown = MAX_HIT_COOLDOWN
        }
        if (lives < 1) {
            lives = 0
            if (CyanBatGameActivity.soundsEnabled) {
                CyanBatGameActivity.gameAssets.audio.deathSound.play(100f)
            }
            alive = false
            gameScreen.saveHighscore()
            CyanBatGameActivity.gameAssets.audio.gameTrack.apply {
                stop()
                isLooping = false
            }
            if (CyanBatGameActivity.musicEnabled) {
                CyanBatGameActivity.gameAssets.audio.gameOverMusic.play()
            }
        }
    }

    companion object {
        const val DEFAULT_WIDTH = 45
        private const val ANIM_TICK_INTERVAL = 0.2f
        private const val TICK_INITIAL = 0.009f
        private const val MAX_HIT_COOLDOWN = 0.5f
    }
}
