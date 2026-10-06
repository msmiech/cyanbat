package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.game_over_hint
import at.smiech.cyanbat.resources.score
import at.smiech.cyanbat.resources.stage_highscore
import at.smiech.cyanbat.util.GAME_OVER_ARMING_SECONDS
import at.smiech.cyanbat.util.HIT_VIBRATION_MILLIS
import at.smiech.engine.EngineColors
import at.smiech.engine.Game
import at.smiech.engine.GameButton
import at.smiech.engine.Screen

/**
 * The run is lost: the artwork saying so, the way back to the menu, and under it what the run
 * scored against the stage's highscore.
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
            // is black, and so is the frame's, so it shows no edge. Its GAME OVER is English in
            // every language; the line under it is not, so it is drawn here rather than in it (see
            // tools/generate_game_over.py).
            val artwork = env.assets.graphics.gameOver
            val artworkLeft = (game.frameBufferWidth - artwork.width) / 2
            val artworkTop = (game.frameBufferHeight - artwork.height) / 2
            graphics.drawPixmap(artwork, artworkLeft, artworkTop)

            // Every line under GAME OVER is centered on its column, each on its own measured width.
            fun drawCentered(text: String, baseline: Int, size: Int, color: Int) = graphics.drawString(
                text,
                artworkLeft + ARTWORK_CENTER_X - graphics.measureString(text, size) / 2,
                artworkTop + baseline,
                size,
                color,
            )
            drawCentered(env.text[Res.string.game_over_hint], HINT_BASELINE, HINT_SIZE, EngineColors.WHITE)
            drawCentered(env.text.format(Res.string.score, score), SCORE_BASELINE, 20, EngineColors.WHITE)
            drawCentered(
                env.text.format(Res.string.stage_highscore, highscore),
                HIGHSCORE_BASELINE,
                15,
                EngineColors.CYAN,
            )
        }
    }

    internal companion object {
        /**
         * The column gameover.png centers its GAME OVER on, 12px right of its own center, from its
         * left edge, which its line under GAME OVER was centered on too. Read off the artwork.
         * Centered on the artwork instead, the lines under it would sit visibly left of it.
         */
        const val ARTWORK_CENTER_X = 252

        /**
         * The line under GAME OVER: on the baseline the artwork had it on, from the artwork's top
         * edge, and at the size that gives the English the width it had there. Larger than the
         * score, as it was.
         */
        const val HINT_BASELINE = 234
        const val HINT_SIZE = 24

        /** The score's two lines' baselines, from the artwork's top edge: in the dark band under it. */
        const val SCORE_BASELINE = 270
        const val HIGHSCORE_BASELINE = 293
    }
}
