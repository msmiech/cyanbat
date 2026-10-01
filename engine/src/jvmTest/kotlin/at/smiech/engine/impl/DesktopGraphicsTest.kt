package at.smiech.engine.impl

import at.smiech.engine.EngineColors
import at.smiech.engine.Raster
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
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

    /**
     * The shapes come out exactly as [Raster] works them out - which is what Android draws too -
     * and not as Java2D's own `fillOval` and `drawLine` would have them.
     *
     * Drawn translucent over black, so that a pixel blended twice, or an edge pixel only partly
     * covered, would show up as a second shade of gray.
     */
    @Test
    fun `ovals outlines and lines land on exactly the pixels Raster gives them`() {
        val gray = EngineColors.withAlpha(EngineColors.WHITE, 0.5f)
        val cases = listOf(
            Triple("oval", lit { it.drawOval(5, 4, 37, 29, gray) }, runs { Raster.oval(5, 4, 37, 29, it) }),
            Triple(
                "outline",
                lit { it.drawOvalOutline(5, 4, 37, 29, gray) },
                runs { Raster.ovalOutline(5, 4, 37, 29, it) },
            ),
            Triple("line", lit { it.drawLine(3, 40, 58, 9, gray) }, runs { Raster.line(3, 40, 58, 9, it) }),
        )
        for ((shape, drawn, expected) in cases) {
            assertEquals(expected, drawn.keys, "the $shape's pixels")
            assertEquals(1, drawn.values.toSet().size, "the $shape in more than one shade")
        }
    }

    /** Every pixel [draw] changes on a black frame, with the color it leaves there. */
    private fun lit(draw: (DesktopGraphics) -> Unit): Map<Pair<Int, Int>, Int> {
        val frame = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB)
        draw(DesktopGraphics(frame) { error("no assets here") })
        val lit = HashMap<Pair<Int, Int>, Int>()
        for (y in 0 until frame.height) for (x in 0 until frame.width) {
            val rgb = frame.getRGB(x, y) and 0xFFFFFF
            if (rgb != 0) lit[x to y] = rgb
        }
        return lit
    }

    /** The pixels covered by the rectangles [draw] hands out. */
    private fun runs(draw: ((Int, Int, Int, Int) -> Unit) -> Unit): Set<Pair<Int, Int>> {
        val pixels = HashSet<Pair<Int, Int>>()
        draw { left, top, width, height ->
            for (y in top until top + height) for (x in left until left + width) pixels += x to y
        }
        return pixels
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
