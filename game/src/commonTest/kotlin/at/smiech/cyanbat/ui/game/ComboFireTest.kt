package at.smiech.cyanbat.ui.game

import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComboFireTest {

    private fun fire() = ComboFire(WIDTH, HEIGHT, Random(7))

    /** One tick of a fire fed along the bottom row, from [from] up to [until], at [heat]. */
    private fun ComboFire.tick(heat: Int, from: Int = 20, until: Int = 40) {
        rise()
        for (x in from until until) stoke(x, HEIGHT - 1, heat)
    }

    @Test
    fun `a fire nobody stokes stays dark`() {
        val fire = fire()
        repeat(10) { fire.rise() }
        assertFalse(fire.lit)
        assertEquals(0, fire.flameHeight())

        val g = RectRecordingGraphics()
        fire.draw(g, 0, 0, 1, PALETTE, 8)
        assertTrue(g.rects.isEmpty())
    }

    @Test
    fun `the flames rise off the stoked row`() {
        val fire = fire()
        repeat(30) { fire.tick(heat = 8) }
        assertTrue(fire.lit)
        assertTrue(fire.flameHeight() > 3, "the flames stood only ${fire.flameHeight()} rows tall")
    }

    @Test
    fun `a hotter stoke stands taller flames`() {
        fun averageHeight(heat: Int): Float {
            val fire = fire()
            repeat(20) { fire.tick(heat) }
            var total = 0
            repeat(200) {
                fire.tick(heat)
                total += fire.flameHeight()
            }
            return total / 200f
        }
        assertTrue(averageHeight(10) > averageHeight(4) + 3f)
    }

    /**
     * What a hit does to the combo. The fire is ticked through the rest of the run and has to be
     * out for good once the last of its heat has had time to rise off the top.
     */
    @Test
    fun `an unstoked fire goes out within its own height`() {
        val fire = fire()
        repeat(50) { fire.tick(heat = 20) }

        repeat(HEIGHT) { fire.rise() }

        assertFalse(fire.lit)
        assertEquals(0, fire.flameHeight())
    }

    @Test
    fun `the flames stream back to the left`() {
        val fire = fire()
        val g = RectRecordingGraphics()
        repeat(100) {
            fire.tick(heat = 12)
            fire.draw(g, 0, 0, 1, PALETTE, 12)
        }
        val left = g.rects.filter { it.right <= 20 }.sumOf { it.width }
        val right = g.rects.filter { it.x >= 40 }.sumOf { it.width }
        assertTrue(
            left > right * 2,
            "$left cells burned left of the stoked row and $right right of it"
        )
    }

    @Test
    fun `stokes off the grid are ignored`() {
        val fire = fire()
        fire.stoke(-1, HEIGHT - 1, 10)
        fire.stoke(WIDTH, HEIGHT - 1, 10)
        fire.stoke(0, HEIGHT, 10)
        fire.stoke(0, -1, 10)
        assertFalse(fire.lit)
    }

    /** Fed right up to both edges, where a careless drift would read off the end of a row. */
    @Test
    fun `a fire as wide as its grid stays on it`() {
        val fire = fire()
        val g = RectRecordingGraphics()
        repeat(50) {
            fire.tick(heat = 30, from = 0, until = WIDTH)
            fire.draw(g, 0, 0, 1, PALETTE, 30)
        }
        assertTrue(g.rects.all { it.x >= 0 && it.right <= WIDTH && it.y in 0 until HEIGHT })
    }

    /**
     * The claim the class makes for drawing a run of cells as one rectangle. The combo's own fire at
     * the peak of a flare, across the widest readout there is, burns in some sixteen hundred cells
     * and draws in a little over six hundred rectangles.
     */
    @Test
    fun `a roaring fire draws in well under a rectangle a cell`() {
        val fire = ComboFire(110, 30, Random(7))
        repeat(60) {
            fire.rise()
            for (x in 8 until 100) {
                fire.stoke(x, 22, 16)
                fire.stoke(x, 23, 16)
            }
        }
        val g = RectRecordingGraphics()
        fire.draw(g, 0, 0, 2, PALETTE, 11)
        val cells = g.rects.sumOf { it.width } / 2
        assertTrue(cells > 1_200, "only $cells cells burning")
        assertTrue(g.rects.size < cells / 2, "${g.rects.size} rectangles for $cells cells")
    }

    @Test
    fun `cells are drawn at the cell size`() {
        val fire = fire()
        repeat(20) { fire.tick(heat = 8) }
        val g = RectRecordingGraphics()
        fire.draw(g, 100, 50, 3, PALETTE, 8)
        assertTrue(g.rects.isNotEmpty())
        assertTrue(g.rects.all { it.height == 3 && it.width % 3 == 0 && (it.x - 100) % 3 == 0 && (it.y - 50) % 3 == 0 })
    }

    private companion object {
        const val WIDTH = 60
        const val HEIGHT = 30

        /** Index 0 is never drawn; the rest only need telling apart. */
        val PALETTE = intArrayOf(0, 1, 2, 3, 4, 5, 6)
    }
}

internal data class DrawnRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val color: Int
) {
    val right: Int get() = x + width
}

/** Keeps every rectangle drawn; everything else is a no-op, and text is seven pixels a glyph. */
internal class RectRecordingGraphics : Graphics {
    val rects = mutableListOf<DrawnRect>()
    val strings = mutableListOf<String>()

    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        rects += DrawnRect(x, y, width, height, color)
    }

    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) {
        if (s != null) strings += s
    }

    override fun measureString(s: String, fontSize: Int) = s.length * 7

    override fun newPixmap(filename: String, format: Graphics.PixmapFormat) =
        throw UnsupportedOperationException()

    override fun clear(color: Int) = Unit
    override fun drawPixel(x: Int, y: Int, color: Int) = Unit
    override fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int) = Unit
    override fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int) = Unit
    override fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int
    ) = Unit

    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) = Unit
    override fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ) = Unit

    override fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        rotationDegrees: Float
    ) = Unit

    override fun drawPixmapSilhouette(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        color: Int
    ) = Unit

    override val width = 480
    override val height = 320
}
