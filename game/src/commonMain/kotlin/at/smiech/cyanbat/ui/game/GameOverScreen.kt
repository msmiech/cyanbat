package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.game_over_hint
import at.smiech.cyanbat.resources.game_over_wave
import at.smiech.cyanbat.resources.prompt_main_menu
import at.smiech.cyanbat.resources.score
import at.smiech.cyanbat.resources.stage_highscore
import at.smiech.cyanbat.util.GAME_OVER_ARMING_SECONDS
import at.smiech.cyanbat.util.HIT_VIBRATION_MILLIS
import at.smiech.engine.EngineColors
import at.smiech.engine.Game
import at.smiech.engine.GameButton
import at.smiech.engine.InputMethod
import at.smiech.engine.Screen

/**
 * The run is lost: the artwork saying so, the way back to the menu, and what the run scored against
 * the stage's highscore.
 *
 * @param score what the run that just ended scored.
 * @param highscore the stage's highscore including this run, so a record run shows the same number
 *   twice.
 * @param wave the wave an endless run reached, counted from one; null for a stage's run, whose waves
 *   the score already speaks for.
 */
class GameOverScreen(
    override val game: Game,
    private val env: CyanBatEnvironment,
    private val score: Int,
    private val highscore: Int,
    private val wave: Int? = null,
) : Screen {

    /** Time before a tap or press counts as "leave"; see [GAME_OVER_ARMING_SECONDS]. */
    private var armingTime = GAME_OVER_ARMING_SECONDS

    /** Only taps begun on this screen; the finger that was steering the bat does not count. */
    private val taps = TapDetector()

    override fun update(deltaTime: Float) {
        armingTime -= deltaTime

        val input = game.input

        // Always read the events, so they do not pile up while the screen lingers.
        val tapped = taps.taps(input?.touchEvents.orEmpty()).isNotEmpty()
        val controls = input?.controls
        // Either button leaves, and both are consumed rather than short-circuited, so a press left
        // unread here cannot fire on the next screen.
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
            // The hand-drawn artwork was made for a 480x320 frame, so it is centered on this one;
            // its black ground shows no edge. Its GAME OVER is English in every language; the line
            // under it is translated, so it is drawn here (see tools/generate_game_over.py).
            val artwork = env.assets.graphics.gameOver
            val artworkLeft = (game.frameBufferWidth - artwork.width) / 2
            val artworkTop = (game.frameBufferHeight - artwork.height) / 2
            graphics.drawPixmap(artwork, artworkLeft, artworkTop)

            // Every line under GAME OVER is centered on its column by its measured width.
            fun drawCentered(text: String, baseline: Int, size: Int, color: Int) =
                graphics.drawString(
                    text,
                    artworkLeft + ARTWORK_CENTER_X - graphics.measureString(text, size) / 2,
                    artworkTop + baseline,
                    size,
                    color,
                )
            // Touched, the way back is said; with a keyboard or a controller, its button is drawn.
            val method = game.input?.controls?.inputMethod ?: InputMethod.TOUCH
            if (method == InputMethod.TOUCH) {
                drawCentered(
                    env.text[Res.string.game_over_hint],
                    HINT_BASELINE,
                    HINT_SIZE,
                    EngineColors.WHITE
                )
            } else {
                val prompt = Prompt(PromptAction.CONFIRM, env.text[Res.string.prompt_main_menu])
                ButtonPrompts(graphics).drawCentered(
                    method,
                    listOf(prompt),
                    artworkLeft + ARTWORK_CENTER_X,
                    artworkTop + HINT_BASELINE,
                    HINT_SIZE,
                    EngineColors.WHITE,
                )
            }
            drawCentered(
                env.text.format(Res.string.score, score),
                SCORE_BASELINE,
                20,
                EngineColors.WHITE
            )
            drawCentered(
                env.text.format(Res.string.stage_highscore, highscore),
                HIGHSCORE_BASELINE,
                15,
                EngineColors.CYAN,
            )
            wave?.let {
                drawCentered(
                    env.text.format(Res.string.game_over_wave, it),
                    WAVE_BASELINE,
                    15,
                    EngineColors.YELLOW
                )
            }
        }
    }

    internal companion object {
        /**
         * The column gameover.png centers its GAME OVER on, from its left edge: 12 px right of the
         * artwork's center, read off the artwork. Centered on the artwork instead, the lines under
         * it would sit visibly left of the title.
         */
        const val ARTWORK_CENTER_X = 252

        /**
         * The line under GAME OVER: on the baseline the artwork had it on, from the artwork's top
         * edge, at the size that gives the English its original width.
         */
        const val HINT_BASELINE = 234
        const val HINT_SIZE = 24

        /** The score's two lines' baselines, from the artwork's top edge: in the dark band under it. */
        const val SCORE_BASELINE = 270
        const val HIGHSCORE_BASELINE = 293

        /** How far an endless run got, under the highscore, still inside the frame. */
        const val WAVE_BASELINE = 315
    }
}
