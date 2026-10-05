package at.smiech.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The pixels [Raster] hands the backends, which is what both of them draw: so these are the shapes
 * on every platform, and the place to pin them down.
 */
class RasterTest {

    /** The pixels a shape hands out, failing if any of them is handed out twice. */
    private fun pixelsOf(draw: ((Int, Int, Int, Int) -> Unit) -> Unit): Set<Pair<Int, Int>> {
        val pixels = mutableSetOf<Pair<Int, Int>>()
        draw { left, top, width, height ->
            assertTrue(width > 0 && height > 0, "an empty run at $left, $top")
            for (y in top until top + height) for (x in left until left + width) {
                // Twice would blend a translucent shape twice there, and leave a darker speck.
                assertTrue(pixels.add(x to y), "pixel $x, $y handed out twice")
            }
        }
        return pixels
    }

    private fun oval(x: Int, y: Int, width: Int, height: Int) =
        pixelsOf { fill -> Raster.oval(x, y, width, height, fill) }

    private fun outline(x: Int, y: Int, width: Int, height: Int) =
        pixelsOf { fill -> Raster.ovalOutline(x, y, width, height, fill) }

    private fun interior(x: Int, y: Int, width: Int, height: Int) =
        pixelsOf { fill -> Raster.ovalInterior(x, y, width, height, fill) }

    private fun line(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int) =
        pixelsOf { fill -> Raster.line(xFrom, yFrom, xTo, yTo, fill) }

    /** [pixels] drawn as rows of text, the box at the origin [width] by [height]. */
    private fun picture(pixels: Set<Pair<Int, Int>>, width: Int, height: Int): String =
        (0 until height).joinToString("\n") { y ->
            (0 until width).joinToString("") { x -> if ((x to y) in pixels) "#" else "." }
        }

    // region ovals

    /**
     * The definition, pixel by pixel, against what the rows are worked out as: a square root, a
     * parity and a run of rows the same width at a time.
     */
    @Test
    fun `an oval covers exactly the pixels whose centers lie inside it`() {
        for (width in 1..40) for (height in 1..40) {
            val expected = mutableSetOf<Pair<Int, Int>>()
            val w = width.toLong()
            val h = height.toLong()
            for (row in 0 until height) for (column in 0 until width) {
                // Offsets from the center in half pixels, so the test stays in whole numbers.
                val u = (2 * column + 1 - width).toLong()
                val v = (2 * row + 1 - height).toLong()
                if (u * u * h * h + v * v * w * w <= w * w * h * h) expected += (3 + column) to (-2 + row)
            }
            assertEquals(expected, oval(3, -2, width, height), "the $width by $height oval")
        }
    }

    @Test
    fun `a circle reaches all four sides of its box`() {
        for (size in 1..60) {
            val pixels = oval(10, 20, size, size)
            assertEquals(10, pixels.minOf { it.first }, "left of $size")
            assertEquals(10 + size - 1, pixels.maxOf { it.first }, "right of $size")
            assertEquals(20, pixels.minOf { it.second }, "top of $size")
            assertEquals(20 + size - 1, pixels.maxOf { it.second }, "bottom of $size")
        }
    }

    @Test
    fun `small circles come out the way pixel art draws them`() {
        assertEquals("#", picture(oval(0, 0, 1, 1), 1, 1))
        assertEquals("##\n##", picture(oval(0, 0, 2, 2), 2, 2))
        assertEquals(".##.\n####\n####\n.##.", picture(oval(0, 0, 4, 4), 4, 4))
        assertEquals(
            """
            ..####..
            .######.
            ########
            ########
            ########
            ########
            .######.
            ..####..
            """.trimIndent(),
            picture(oval(0, 0, 8, 8), 8, 8),
        )
    }

    @Test
    fun `an empty box draws nothing`() {
        assertTrue(oval(5, 5, 0, 10).isEmpty())
        assertTrue(oval(5, 5, 10, 0).isEmpty())
        assertTrue(oval(5, 5, -4, 6).isEmpty())
        assertTrue(outline(5, 5, 0, 0).isEmpty())
    }

    // endregion

    // region outlines

    @Test
    fun `an outline is the oval less its inside`() {
        for (width in 1..30) for (height in 1..30) {
            val oval = oval(0, 0, width, height)
            val interior = interior(0, 0, width, height)
            assertTrue(
                oval.containsAll(interior),
                "the $width by $height oval's inside spills out of it"
            )
            assertEquals(
                oval - interior,
                outline(0, 0, width, height),
                "the $width by $height outline"
            )
        }
    }

    @Test
    fun `the inside of an oval is the pixels walled in on all four sides`() {
        for (width in 1..30) for (height in 1..30) {
            val oval = oval(0, 0, width, height)
            val walledIn = oval.filter { (x, y) ->
                (x - 1 to y) in oval && (x + 1 to y) in oval && (x to y - 1) in oval && (x to y + 1) in oval
            }.toSet()
            assertEquals(
                walledIn,
                interior(0, 0, width, height),
                "the inside of the $width by $height oval"
            )
        }
    }

    @Test
    fun `an outline is one pixel thick all the way round`() {
        assertEquals(
            """
            ..####..
            .#....#.
            #......#
            #......#
            #......#
            #......#
            .#....#.
            ..####..
            """.trimIndent(),
            picture(outline(0, 0, 8, 8), 8, 8),
        )
    }

    // endregion

    // region lines

    @Test
    fun `a line lights both its ends and the same pixels whichever way it is drawn`() {
        for (x0 in -5..5) for (y0 in -5..5) for (x1 in -5..5) for (y1 in -5..5) {
            val there = line(x0, y0, x1, y1)
            assertTrue(
                (x0 to y0) in there && (x1 to y1) in there,
                "$x0 $y0 to $x1 $y1 misses an end"
            )
            assertEquals(there, line(x1, y1, x0, y0), "$x0 $y0 to $x1 $y1 drawn back the other way")
        }
    }

    /** One pixel in every column a shallow line crosses, each the one nearest the true line. */
    @Test
    fun `a line lights the pixel nearest it in each column or row it crosses`() {
        for (dx in -12..12) for (dy in -12..12) {
            if (dx == 0 && dy == 0) continue
            val pixels = line(0, 0, dx, dy)
            val shallow = abs(dx) >= abs(dy)
            val span = if (shallow) abs(dx) else abs(dy)
            assertEquals(span + 1, pixels.size, "0 0 to $dx $dy")
            for ((x, y) in pixels) {
                // How far the pixel sits from the true line, across it: within half a pixel.
                val off = if (shallow) abs(y * dx - x * dy) / abs(dx).toFloat()
                else abs(x * dy - y * dx) / abs(dy).toFloat()
                assertTrue(off <= 0.5f, "0 0 to $dx $dy lights $x $y, $off off the line")
            }
        }
    }

    @Test
    fun `a line with no length is one pixel`() {
        assertEquals(setOf(7 to 9), line(7, 9, 7, 9))
    }

    @Test
    fun `straight lines come out as one run`() {
        var runs = 0
        Raster.line(2, 5, 9, 5) { _, _, _, _ -> runs++ }
        Raster.line(4, 8, 4, 1) { _, _, _, _ -> runs++ }
        assertEquals(2, runs)
        assertEquals((2..9).map { it to 5 }.toSet(), line(9, 5, 2, 5))
        assertEquals((1..8).map { 4 to it }.toSet(), line(4, 8, 4, 1))
    }

    // endregion
}
