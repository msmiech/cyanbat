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
            // The artwork was drawn for the frame the game had before, 480x320, and is hand-drawn
            // rather than generated, so it is centered on this one rather than redrawn. Its ground
            // is black, and so is the frame's, so it shows no edge.
            val artwork = env.assets.graphics.gameOver
            val artworkLeft = (game.frameBufferWidth - artwork.width) / 2
            val artworkTop = (game.frameBufferHeight - artwork.height) / 2
            graphics.drawPixmap(artwork, artworkLeft, artworkTop)
            // In the dark band the artwork leaves under its own two lines. The two share a left
            // edge, so they read as one block, and the block is centered by the wider of them on
            // the column the artwork's lines are.
            val scoreLine = "Score: $score"
            val highscoreLine = "Highscore: $highscore"
            val width = maxOf(
                graphics.measureString(scoreLine, 20),
                graphics.measureString(highscoreLine, 15),
            )
            val left = artworkLeft + ARTWORK_CENTER_X - width / 2
            graphics.drawString(
                scoreLine,
                left,
                artworkTop + SCORE_BASELINE,
                20,
                EngineColors.WHITE
            )
            graphics.drawString(
                highscoreLine,
                left,
                artworkTop + HIGHSCORE_BASELINE,
                15,
                EngineColors.CYAN
            )
        }
    }

    private companion object {
        /**
         * The column gameover.png centers its own two lines on, 12px right of its own center, from
         * its left edge. Read off the artwork. Centered on the artwork instead, the score would sit
         * visibly left of the lines above it.
         */
        const val ARTWORK_CENTER_X = 252

        /** The two lines' baselines, from the artwork's top edge: in the dark band under its own. */
        const val SCORE_BASELINE = 270
        const val HIGHSCORE_BASELINE = 293
    }
}
