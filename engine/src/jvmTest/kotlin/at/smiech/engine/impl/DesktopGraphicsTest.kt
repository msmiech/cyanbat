package at.smiech.engine.impl

import at.smiech.engine.EngineColors
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [DesktopGraphics.measureString] held against the pixels [DesktopGraphics.drawString] actually
 * lays down, in whatever face this JVM resolves SansSerif to: Arial on Windows, and on a Linux
 * machine such as CI's, usually DejaVu Sans.
 *
 * The game right-aligns, centers and wraps text by these widths. A width that came up short would
 * run text off the frame or out of its card; one that came up long would leave it short of the
 * edge it was aligned to.
 */
class DesktopGraphicsTest {

    @Test
    fun `the measured width ends where the drawn text does`() {
        for ((text, size) in GAME_TEXT) {
            val frame = BufferedImage(320, 48, BufferedImage.TYPE_INT_RGB)
            val graphics = DesktopGraphics(frame) { error("no assets here") }
            val penEnd = PEN_X + graphics.measureString(text, size)
            graphics.drawString(text, PEN_X, 36, size, EngineColors.WHITE)

            val inked = (0 until frame.width).filter { x ->
                (0 until frame.height).any { y -> frame.getRGB(x, y) == EngineColors.WHITE }
            }
            assertTrue(inked.isNotEmpty(), "\"$text\" at ${size}px drew nothing")
            assertTrue(
                inked.last() < penEnd,
                "\"$text\" at ${size}px inks column ${inked.last()}, past its measured end at $penEnd",
            )
            // All that should be left over is the last glyph's own side bearing: a sliver of the
            // size, plus a pixel or two of hinting.
            assertTrue(
                penEnd - inked.last() <= size / 4 + 2,
                "\"$text\" at ${size}px stops at column ${inked.last()}, short of its measured end at $penEnd",
            )
        }
    }

    private companion object {
        const val PEN_X = 10

        /** One string of each kind the game lays out by its width, at the size it draws it. */
        val GAME_TEXT = listOf(
            "Level: 99" to 15,
            "00:00" to 18,
            "QUEEN ENRAGED" to 26,
            "+25 max health, healed" to 11,
        )
    }
}
