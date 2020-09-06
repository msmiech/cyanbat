package at.smiech.cyanbat.gameobjects.impl

import android.graphics.Rect
import android.util.Log
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Pixmap
import at.smiech.cyanbat.activities.CyanBatGameActivity
import at.smiech.cyanbat.gameobjects.Collidable
import at.smiech.cyanbat.gameobjects.GameObject
import at.smiech.cyanbat.gameobjects.PixmapGameObject
import at.smiech.cyanbat.screens.CyanBatBaseScreen
import at.smiech.cyanbat.screens.GameOverScreen
import at.smiech.cyanbat.screens.GameScreen
import java.util.*

class CyanBat(x: Int, y: Int, width: Int, height: Int, pm: Pixmap, private val gs: GameScreen) : PixmapGameObject(Rect(x, y, x + DEFAULT_WIDTH, y + height), pm), Collidable {

    private var animTickTime = 0f
    var alive = true
    var lives = 3
    private var animTick: Int = 0
    private var tickTime = 0f
    private val tick = TICK_INITIAL
    private var srcX: Int = 0
    private val curvatures = ArrayList<CyanTrail>()
    private var hitCooldown = MAX_HIT_COOLDOWN // cooldown grace period in seconds

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        if (GameObject.DEBUG)
            Log.d(GameObject.TAG, "updateBat")
        updateLogic(deltaTime, touchEvents)
        updateAnimation(deltaTime)
        updateCurvatations()

        if (CyanBatGameActivity.SHOOTING_ENABLED) {
            if (gs.game.input!!.touchEvents!!.size > 1)
                shoot()
        }
        super.update(deltaTime, touchEvents)
    }

    private fun updateCurvatations() {
        val potentialRect = Rect(rectangle.left,
                rectangle.top + rectangle.height() / 2, rectangle.left + rectangle.width() / 4,
                rectangle.bottom)
        for (i in curvatures.indices) {
            val curv = curvatures[i]
            if (Rect.intersects(curv.rectangle, potentialRect)) {
                return
            }
        }

        curvatures.filter { curv -> curv.removeMe }.forEach { curv -> curvatures.remove(curv) }

        potentialRect.left -= 2
        potentialRect.right -= 1
        val go = CyanTrail(potentialRect)
        curvatures.add(go)
        gs.gameObjects.add(go)
    }

    private fun shoot() {
        if (Shot.count > 1)
            return
        val shot = Shot(Rect(rectangle.left, rectangle.top,
                rectangle.left + CyanBatGameActivity.shot.width, rectangle.top + CyanBatGameActivity.shot.height), CyanBatGameActivity.shot, this)
        gs.gameObjects.add(shot)
        gs.colChk.addObjectToCheck(shot)
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
            if (!touchEvents.isEmpty()) {
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
            if (rectangle.top > CyanBatBaseScreen.DISPLAY_WIDTH)
                gs.game.setScreen(GameOverScreen(gs.game))
        }
    }

    private fun move(touch: TouchEvent) {
        if (GameObject.DEBUG)
            Log.d(GameObject.TAG, "moveBat")
        if (touch.x > rectangle.left) {
            if (rectangle.right < CyanBatBaseScreen.DISPLAY_HEIGHT) {
                velocity.x = 3f
            }
        } else {
            if (rectangle.left > 0) {
                velocity.x = -3f
            }
        }
        if (touch.y > rectangle.bottom) {
            if (rectangle.bottom < CyanBatBaseScreen.DISPLAY_WIDTH) {
                velocity.y = 3f
            }
        } else {
            if (rectangle.top > 0) {
                velocity.y = -3f
            }
        }

    }

    override fun draw(g: Graphics) {
        if (GameObject.DEBUG)
            Log.d(GameObject.TAG, "drawBat")
        g.drawPixmap(pixmap, rectangle.left, rectangle.top, srcX, 0, DEFAULT_WIDTH,
                rectangle.height())
        super.draw(g)
    }

    override fun hit() {
        CyanBatGameActivity.vib.vibrate(250)
        if (hitCooldown <= 0.01f) {
            lives -= 1
            hitCooldown = MAX_HIT_COOLDOWN
        }
        if (lives < 1) {
            lives = 0
            if (CyanBatGameActivity.soundsEnabled) {
                CyanBatGameActivity.deathSound.play(100f)
            }
            alive = false
            gs.saveHighscore()
            CyanBatGameActivity.musicPlayer.stopMusic()
            gs.interruptThreads()
            if (CyanBatGameActivity.musicEnabled) {
                CyanBatGameActivity.gameOverMusic.play()
            }
        }
    }

    companion object {

        val DEFAULT_WIDTH = 45
        private val ANIM_TICK_INTERVAL = 0.2f
        private val TICK_INITIAL = 0.009f
        private val MAX_HIT_COOLDOWN = 0.5f
    }
}
