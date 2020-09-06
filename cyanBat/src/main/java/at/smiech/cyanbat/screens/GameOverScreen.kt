package at.smiech.cyanbat.screens

import android.content.Intent

import at.grueneis.game.framework.Game
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.smiech.cyanbat.activities.CyanBatGameActivity
import at.smiech.cyanbat.activities.MainMenuActivity

class GameOverScreen(game: Game) : CyanBatBaseScreen(game) {

    private var touchEvents: List<TouchEvent>? = null
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
        touchEvents = super.game.input!!.touchEvents
        for (event in touchEvents!!) {
            if (event.type == TouchEvent.TOUCH_UP) {
                CyanBatGameActivity.vib.vibrate(250)
                //game.setScreen(new StartScreen(game));
                val mainMenuIntent = Intent(super.game.context, MainMenuActivity::class.java)
                super.game.context.startActivity(mainMenuIntent)
            }
        }
        super.update(deltaTime)
    }

    override fun present(deltaTime: Float) {
        drawMap(g!!)
        g!!.drawPixmap(CyanBatGameActivity.gameOver, 0, 0)
    }

    override fun pause() {

    }

    override fun resume() {

    }

    override fun dispose() {

    }

}
