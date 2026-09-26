package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.util.GAME_OVER_ARMING_SECONDS
import at.smiech.cyanbat.util.HIT_VIBRATION_MILLIS
import at.smiech.engine.EngineColors
import at.smiech.engine.Game
import at.smiech.engine.GameButton
import at.smiech.engine.Screen

/**
 * The run is lost: the artwork saying so, and under it what the run scored against the stage's
 * highscore.
 *
 * @param score what the run that just ended scored.
 * @param highscore the stage's highscore with this run already counted in it, so a run that set
 *   the record shows the same number twice - which is how the player can tell that it did.
 */
class GameOverScreen(
    override val game: Game,
    private val env: CyanBatEnvironment,
    private val score: Int,
    private val highscore: Int,
) : Screen {

    /** Time before a tap or press counts as "leave"; see [GAME_OVER_ARMING_SECONDS]. */
    private var armingTime = GAME_OVER_ARMING_SECONDS

    /** Only taps begun on this screen; the finger that was steering the bat does not count. */
    private val taps = TapDetector()

    override fun update(deltaTime: Float) {
        armingTime -= deltaTime

        val input = game.input

        // Read the buffer whatever happens, so a screen that lingers does not hoard events.
        val tapped = taps.taps(input?.touchEvents.orEmpty()).isNotEmpty()
        val controls = input?.controls
        // Both buttons, and each consumed on its own rather than short-circuited: a player who
        // died on a keyboard has no reason to guess which of the two this screen wanted, and a
        // press left unread here would fire on the next screen.
        val confirmed = controls?.consumePress(GameButton.CONFIRM) == true
        val backed = controls?.consumePress(GameButton.BACK) == true

        if ((tapped || confirmed || backed) && armingTime <= 0f) {
            env.haptics.vibrate(HIT_VIBRATION_MILLIS)
            env.onExitToMenu()
        }
    }

    override fun present(deltaTime: Float) {
        game.graphics?.let { graphics ->
            graphics.clear(EngineColors.BLACK)
            graphics.drawPixmap(env.assets.graphics.gameOver, 0, 0)
            // In the dark band the artwork leaves under its own two lines, and aligned with each
            // other at an x that centers a four digit score - the Graphics API cannot measure one.
            graphics.drawString("Score: $score", 185, 270, 20, EngineColors.WHITE)
            graphics.drawString("Highscore: $highscore", 185, 293, 15, EngineColors.CYAN)
        }
    }
}
