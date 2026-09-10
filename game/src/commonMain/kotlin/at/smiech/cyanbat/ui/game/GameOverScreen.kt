package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.util.HIT_VIBRATION_MILLIS
import at.smiech.engine.EngineColors
import at.smiech.engine.Game
import at.smiech.engine.GameButton
import at.smiech.engine.Input.TouchEvent
import at.smiech.engine.Screen

class GameOverScreen(
    override val game: Game,
    private val env: CyanBatEnvironment,
) : Screen {

    override fun update(deltaTime: Float) {
        val input = game.input

        // Read the buffer whatever happens, so a screen that lingers does not hoard events.
        val tapped = input?.touchEvents?.any { it.type == TouchEvent.TOUCH_UP } == true
        val controls = input?.controls
        // Both buttons, and each consumed on its own rather than short-circuited: a player who
        // died on a keyboard has no reason to guess which of the two this screen wanted, and a
        // press left unread here would fire on the next screen.
        val confirmed = controls?.consumePress(GameButton.CONFIRM) == true
        val backed = controls?.consumePress(GameButton.BACK) == true

        if (tapped || confirmed || backed) {
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
