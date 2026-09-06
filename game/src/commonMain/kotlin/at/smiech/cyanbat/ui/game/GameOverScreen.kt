package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.util.HIT_VIBRATION_MILLIS
import at.smiech.engine.EngineColors
import at.smiech.engine.Game
import at.smiech.engine.Input.TouchEvent
import at.smiech.engine.Screen

class GameOverScreen(
    override val game: Game,
    private val env: CyanBatEnvironment,
) : Screen {

    override fun update(deltaTime: Float) {
        if (game.input?.touchEvents?.any { it.type == TouchEvent.TOUCH_UP } == true) {
            env.haptics.vibrate(HIT_VIBRATION_MILLIS)
            env.onExitToMenu()
        }
    }

    override fun present(deltaTime: Float) {
        game.graphics?.let { graphics ->
            graphics.clear(EngineColors.BLACK)
            graphics.drawPixmap(env.assets.graphics.gameOver, 0, 0)
        }
    }
}
