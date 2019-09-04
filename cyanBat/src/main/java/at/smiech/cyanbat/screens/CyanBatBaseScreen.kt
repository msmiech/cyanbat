package at.smiech.cyanbat.screens

import android.graphics.Color

import at.grueneis.game.framework.Game
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Screen

abstract class CyanBatBaseScreen(game: Game) : Screen(game) {



    fun drawMap(g: Graphics) {
        g.clear(Color.BLACK)
    }


    // Default screen implementations - override if needed
    override fun update(deltaTime: Float) {
        // empty default method
    }

    override fun present(deltaTime: Float) {
        // empty default method
    }

    override fun pause() {
        // empty default method

    }

    override fun resume() {
        // empty default method

    }

    override fun dispose() {
        // empty default method

    }

    companion object {
        var DISPLAY_HEIGHT = 480
        var DISPLAY_WIDTH = 320
    }

}
