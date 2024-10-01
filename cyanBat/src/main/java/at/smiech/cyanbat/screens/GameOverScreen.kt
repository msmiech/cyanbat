package at.smiech.cyanbat.screens

import android.content.Intent
import android.graphics.Color

import at.grueneis.game.framework.Game
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.smiech.cyanbat.activities.CyanBatGameActivity
import at.smiech.cyanbat.activities.MainMenuActivity

class GameOverScreen(game: Game) : CyanBatBaseScreen(game) {

    private var g: Graphics? = null

    init {
        init()

    }

    private fun init() {
        g = super.game.graphics
        System.gc()
        initSounds()
    }

    private fun initSounds() {
        if (CyanBatGameActivity.gameTrack.isPlaying) {
            CyanBatGameActivity.gameTrack.stop()
            CyanBatGameActivity.gameTrack.isLooping = false
        }
    }

    override fun update(deltaTime: Float) {
        if (game.input?.touchEvents?.any { it.type == TouchEvent.TOUCH_UP } == true) {
            CyanBatGameActivity.vib.vibrate(250)
            val mainMenuIntent = Intent(super.game.context, MainMenuActivity::class.java)
            super.game.context.startActivity(mainMenuIntent)
        }
        super.update(deltaTime)
    }

    override fun present(deltaTime: Float) {
        g?.clear(Color.BLACK)
        g?.drawPixmap(CyanBatGameActivity.gameOver, 0, 0)
    }

    override fun pause() {

    }

    override fun resume() {

    }

    override fun dispose() {

    }

}
