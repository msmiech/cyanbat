package at.smiech.cyanbat.screen

import android.content.Intent
import android.graphics.Color
import at.grueneis.game.framework.Game
import at.grueneis.game.framework.Input.TouchEvent
import at.smiech.cyanbat.MainMenuActivity
import at.smiech.cyanbat.activities.CyanBatGameActivity

class GameOverScreen(game: Game) : CyanBatBaseScreen(game) {

    init {
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
            game.context.startActivity(mainMenuIntent)
        }
        super.update(deltaTime)
    }

    override fun present(deltaTime: Float) {
        game.graphics?.let { graphics ->
            graphics.clear(Color.BLACK)
            graphics.drawPixmap(CyanBatGameActivity.gameOver, 0, 0)
        }
    }
}
