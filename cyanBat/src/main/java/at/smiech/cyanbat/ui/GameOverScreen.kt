package at.smiech.cyanbat.ui

import android.content.Intent
import android.graphics.Color
import at.grueneis.game.framework.Game
import at.grueneis.game.framework.Input.TouchEvent
import at.smiech.cyanbat.MainActivity
import at.smiech.cyanbat.activity.CyanBatGameActivity

class GameOverScreen(game: Game) : CyanBatBaseScreen(game) {
    init {
        initSounds()
    }

    private fun initSounds() {
        CyanBatGameActivity.gameAssets.audio.gameTrack.apply {
            if (isPlaying) {
                stop()
                isLooping = false
            }
        }
    }

    override fun update(deltaTime: Float) {
        if (game.input?.touchEvents?.any { it.type == TouchEvent.TOUCH_UP } == true) {
            CyanBatGameActivity.gameAssets.vib.vibrate(250)
            val mainMenuIntent = Intent(super.game.context, MainActivity::class.java)
            game.context.startActivity(mainMenuIntent)
        }
        super.update(deltaTime)
    }

    override fun present(deltaTime: Float) {
        game.graphics?.let { graphics ->
            graphics.clear(Color.BLACK)
            graphics.drawPixmap(CyanBatGameActivity.gameAssets.graphics.gameOver, 0, 0)
        }
    }
}
