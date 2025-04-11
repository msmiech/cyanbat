package at.smiech.cyanbat.ui.game

import android.content.Intent
import android.graphics.Color
import android.os.VibrationEffect
import android.os.VibrationEffect.DEFAULT_AMPLITUDE
import at.grueneis.game.framework.Game
import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Screen
import at.smiech.cyanbat.MainActivity
import at.smiech.cyanbat.activity.CyanBatGameActivity

class GameOverScreen(override val game: Game) : Screen {
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
            CyanBatGameActivity.gameAssets.vib.vibrate(
                VibrationEffect.createOneShot(
                    250,
                    DEFAULT_AMPLITUDE
                )
            )
            val mainMenuIntent = Intent(game.context, MainActivity::class.java)
            game.context.startActivity(mainMenuIntent)
        }
    }

    override fun present(deltaTime: Float) {
        game.graphics?.let { graphics ->
            graphics.clear(Color.BLACK)
            graphics.drawPixmap(CyanBatGameActivity.gameAssets.graphics.gameOver, 0, 0)
        }
    }
}
