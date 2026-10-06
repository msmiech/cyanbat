package at.smiech.cyanbat.ui.game

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.createFontFamilyResolver
import at.smiech.cyanbat.progress.PowerUp
import at.smiech.cyanbat.resource.GameText
import at.smiech.cyanbat.resource.LANGUAGES
import at.smiech.cyanbat.resource.textIn
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.allStringResources
import at.smiech.cyanbat.resources.banner_second_life
import at.smiech.cyanbat.resources.banner_wave
import at.smiech.cyanbat.resources.game_over_hint
import at.smiech.cyanbat.resources.hud_level
import at.smiech.cyanbat.resources.hud_wave
import at.smiech.cyanbat.resources.level_up_choose
import at.smiech.cyanbat.resources.level_up_title
import at.smiech.cyanbat.resources.pause_quit
import at.smiech.cyanbat.resources.pause_resume
import at.smiech.cyanbat.resources.pause_title
import at.smiech.cyanbat.resources.score
import at.smiech.cyanbat.resources.stage_1_name
import at.smiech.cyanbat.resources.stage_2_name
import at.smiech.cyanbat.resources.stage_3_name
import at.smiech.cyanbat.resources.stage_4_name
import at.smiech.cyanbat.resources.stage_complete_continue
import at.smiech.cyanbat.resources.stage_complete_fresh_start
import at.smiech.cyanbat.resources.stage_complete_menu
import at.smiech.cyanbat.resources.stage_complete_next
import at.smiech.cyanbat.resources.stage_complete_title
import at.smiech.cyanbat.resources.stage_highscore
import at.smiech.cyanbat.util.BANNER_FONT_SIZE
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.cyanbat.util.POWER_UP_CARD_PADDING
import at.smiech.cyanbat.util.POWER_UP_CARD_WIDTH
import at.smiech.cyanbat.util.STAGE_TIMER_FONT_SIZE
import at.smiech.engine.impl.ComposeGraphics
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every line the run draws fits where it is drawn, in every language the game speaks.
 *
 * The run's text has fixed sizes and fixed room, laid out for English, and a translation is
 * usually longer. Measured with the platform's own face, as the game measures it, which is Arial
 * on Windows and usually DejaVu Sans, the wider, on Linux. The sizes are the ones [GameScreen] draws
 * each line at.
 */
class GameTextLayoutTest {

    private val g = ComposeGraphics(
        FRAME_BUFFER_WIDTH,
        FRAME_BUFFER_HEIGHT,
        { ImageBitmap(1, 1) },
        createFontFamilyResolver(),
    )

    private val texts: List<Pair<String, GameText>> =
        LANGUAGES.map { it.toLanguageTag() to textIn(it) }

    /** How far from the frame's edges the HUD keeps, and so how far any line has to. */
    private val margin = 5

    /** The longest numbers the lines are drawn with: a score of a long run, a level well past any. */
    private val score = 9_999_999
    private val level = 99

    private fun assertFits(language: String, line: String, size: Int, room: Int) {
        val width = g.measureString(line, size)
        assertTrue(width <= room, "$language: '$line' is $width wide at $size, with room for $room")
    }

    @Test
    fun `every banner fits across the frame`() {
        val room = FRAME_BUFFER_WIDTH - 2 * margin
        // A boss's lines, which are all of its strings, and the titles of the power-ups, each of
        // which goes up as a banner when it is picked.
        val bosses = Res.allStringResources.filterKeys { it.startsWith("boss_") }.values
        for ((language, text) in texts) {
            val lines = bosses.map { text[it] } + PowerUp.entries.map { text[it.title] } +
                    text.format(Res.string.banner_wave, 5) + text[Res.string.banner_second_life]
            for (line in lines) assertFits(language, line, BANNER_FONT_SIZE, room)
        }
    }

    @Test
    fun `every overlay line fits across the frame`() {
        val room = FRAME_BUFFER_WIDTH - 2 * margin
        for ((language, text) in texts) {
            val stageNames = listOf(
                Res.string.stage_1_name,
                Res.string.stage_2_name,
                Res.string.stage_3_name,
                Res.string.stage_4_name,
            ).map { text[it] }
            // The opening's stage name is the larger of the two it is drawn at.
            for (name in stageNames) assertFits(language, name, 30, room)
            for (title in listOf(
                text[Res.string.pause_title],
                text[Res.string.stage_complete_title],
                text.format(Res.string.level_up_title, level),
            )) assertFits(language, title, 30, room)
            assertFits(language, text.format(Res.string.score, score), 20, room)
            for (line in listOf(
                text[Res.string.pause_resume],
                text[Res.string.pause_quit],
                text.format(Res.string.stage_complete_next, 4),
                text[Res.string.stage_complete_menu],
                text[Res.string.stage_complete_continue],
                text[Res.string.level_up_choose],
                text.format(Res.string.stage_highscore, score),
            )) assertFits(language, line, 15, room)
            assertFits(language, text[Res.string.stage_complete_fresh_start], 13, room)
        }
    }

    /**
     * The lines under GAME OVER are centered on the artwork's own column, a little right of the
     * frame's middle, so they have a little less room on the right than on the left.
     */
    @Test
    fun `the game over screen's lines fit under GAME OVER`() {
        val artwork = ImageIO.read(File("../assets/gameover.png"))
        val center = (FRAME_BUFFER_WIDTH - artwork.width) / 2 + GameOverScreen.ARTWORK_CENTER_X
        val room = 2 * (minOf(center, FRAME_BUFFER_WIDTH - center) - margin)
        for ((language, text) in texts) {
            assertFits(language, text[Res.string.game_over_hint], GameOverScreen.HINT_SIZE, room)
            assertFits(language, text.format(Res.string.score, score), 20, room)
            assertFits(language, text.format(Res.string.stage_highscore, score), 15, room)
        }
    }

    /** The score from the left and the level from the right, each short of the timer between them. */
    @Test
    fun `the top line of the HUD leaves the timer clear`() {
        val timer = g.measureString("88:88", STAGE_TIMER_FONT_SIZE)
        val room = (FRAME_BUFFER_WIDTH - timer) / 2 - margin - TIMER_CLEARANCE
        for ((language, text) in texts) {
            assertFits(language, text.format(Res.string.score, score), 15, room)
            assertFits(language, text.format(Res.string.hud_level, level), 15, room)
            assertFits(
                language,
                text.format(Res.string.hud_wave, 5, 5),
                15,
                FRAME_BUFFER_WIDTH - 2 * margin
            )
        }
    }

    /**
     * A card's title has one line and its words three, under it, wrapped the way the card wraps
     * them. A fourth would still be on the card, but up against its bottom edge.
     */
    @Test
    fun `every power-up card has room for what it says`() {
        val room = POWER_UP_CARD_WIDTH - 2 * POWER_UP_CARD_PADDING
        val held =
            Res.allStringResources.filterKeys { it.startsWith("power_up_") && it.endsWith("_held") }.values
        for ((language, text) in texts) {
            for (powerUp in PowerUp.entries) assertFits(language, text[powerUp.title], 14, room)
            val descriptions = PowerUp.entries.map {
                text.format(it.description, *it.numbers.toTypedArray())
            } + held.map { text[it] }
            for (description in descriptions) {
                val lines = wrapWords(description, room) { g.measureString(it, 11) }
                assertTrue(
                    lines.size <= 3,
                    "$language: '$description' takes ${lines.size} lines: $lines"
                )
                for (line in lines) assertFits(language, line, 11, room)
            }
        }
    }

    private companion object {
        /** The least gap between the timer and the lines on either side of it. */
        const val TIMER_CLEARANCE = 10
    }
}
