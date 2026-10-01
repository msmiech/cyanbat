package at.smiech.engine

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Which pixels the [Graphics] shapes cover, worked out once in common code and handed out as
 * rectangles, so that every backend lights exactly the same pixels.
 *
 * Neither platform can be asked to draw the shapes itself and come out the same as the other.
 * Android's `Paint()` antialiases by default since Android 12, and even with that turned off, Skia
 * and Java2D disagree about which pixels an aliased edge covers: each approximates the curve its
 * own way, Java2D moves a path a quarter of a pixel before filling it, and the two end a line on
 * different pixels. Filled rectangles they agree on to the pixel, which is why everything here
 * comes out as runs of them.
 *
 * Pixel art, so nothing is antialiased: a pixel is in a shape or it is not. And no pixel is handed
 * out twice for one shape, so a shape drawn in a translucent color is blended once all over.
 */
object Raster {

    /**
     * The oval [Graphics.drawOval] fills: the one inscribed in the [width] by [height] box at [x],
     * [y], covering every pixel whose center lies inside it or on its edge. Handed to [fill] top to
     * bottom, one rectangle for each run of rows the same width.
     *
     * Rows of a width come together because a backend pays more for each rectangle than for its
     * pixels, and toward the middle of an oval the rows come several at a time.
     *
     * Symmetric left to right and top to bottom whatever the box, which a pixel-art circle needs
     * and neither platform's own oval quite managed.
     */
    inline fun oval(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fill: (left: Int, top: Int, width: Int, height: Int) -> Unit,
    ) = rowRuns(x, y, width, height, { reach(width, height, it) }, fill)

    /**
     * The inside of the oval [oval] fills: those of its pixels whose four neighbors are all its
     * pixels too. Handed out the way [oval] is.
     *
     * Every row of it is one unbroken run centered on the oval, so like the oval it can be traced
     * as a single polygon, which is how a backend can fill [ovalOutline] in one draw call: the oval
     * and its inside together, filled even-odd.
     */
    inline fun ovalInterior(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fill: (left: Int, top: Int, width: Int, height: Int) -> Unit,
    ) = rowRuns(x, y, width, height, { innerReach(width, height, it) }, fill)

    /**
     * The outline of the oval [oval] fills, one pixel thick: its pixels less its [ovalInterior],
     * which leaves those with a side not shared with another of its pixels. One or two runs a row,
     * top to bottom.
     *
     * It is the oval's own rim rather than a stroke traced around it, so an outline drawn over the
     * same oval filled lands exactly on the edge of the fill.
     */
    inline fun ovalOutline(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fill: (left: Int, top: Int, width: Int, height: Int) -> Unit,
    ) {
        if (width <= 0 || height <= 0) return
        for (row in 0 until height) {
            val outer = reach(width, height, row)
            if (outer < 0) continue
            val inner = innerReach(width, height, row)
            val left = x + (width - 1 - outer) / 2
            if (inner < 0) {
                fill(left, y + row, outer + 1, 1)
            } else {
                val side = (outer - inner) / 2
                fill(left, y + row, side, 1)
                fill(x + (width + 1 + inner) / 2, y + row, side, 1)
            }
        }
    }

    /**
     * The line [Graphics.drawLine] draws, both ends included: in each column it crosses (each row,
     * for a line steeper than it is wide), the pixel nearest the true line, a tie going to the end
     * it is walked from. Handed to [fill] as horizontal runs, or vertical ones for a steep line.
     *
     * Walked from its left end (its top, for a steep line) whichever way round it was given, so a
     * line lights the same pixels in both directions.
     */
    inline fun line(
        xFrom: Int,
        yFrom: Int,
        xTo: Int,
        yTo: Int,
        fill: (left: Int, top: Int, width: Int, height: Int) -> Unit,
    ) {
        val dx = abs(xTo - xFrom)
        val dy = abs(yTo - yFrom)
        if (dx >= dy) {
            val forward = xFrom <= xTo
            val x0 = if (forward) xFrom else xTo
            var y = if (forward) yFrom else yTo
            val step = if ((if (forward) yTo else yFrom) > y) 1 else -1
            var start = x0
            // Bresenham's: how far past halfway to the next row the true line is at the next column,
            // times 2 * dx so that it stays a whole number. Past halfway, that column steps over.
            var error = 2 * dy - dx
            for (column in 1..dx) {
                if (error > 0) {
                    fill(start, y, x0 + column - start, 1)
                    start = x0 + column
                    y += step
                    error -= 2 * dx
                }
                error += 2 * dy
            }
            fill(start, y, x0 + dx + 1 - start, 1)
        } else {
            val forward = yFrom <= yTo
            val y0 = if (forward) yFrom else yTo
            var x = if (forward) xFrom else xTo
            val step = if ((if (forward) xTo else xFrom) > x) 1 else -1
            var start = y0
            var error = 2 * dx - dy
            for (row in 1..dy) {
                if (error > 0) {
                    fill(x, start, 1, y0 + row - start)
                    start = y0 + row
                    x += step
                    error -= 2 * dy
                }
                error += 2 * dx
            }
            fill(x, start, 1, y0 + dy + 1 - start)
        }
    }

    /**
     * One rectangle for each run of the box's rows that reach equally far either side of its
     * center, [reachOf] saying how far each row does in the terms of [reach].
     */
    @PublishedApi
    internal inline fun rowRuns(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        reachOf: (row: Int) -> Int,
        fill: (left: Int, top: Int, width: Int, height: Int) -> Unit,
    ) {
        if (width <= 0 || height <= 0) return
        var first = 0
        var current = reachOf(0)
        // On to one past the last row, which reaches nowhere and so closes the last rectangle.
        for (row in 1..height) {
            val next = if (row < height) reachOf(row) else -1
            if (next == current) continue
            if (current >= 0) fill(x + (width - 1 - current) / 2, y + first, current + 1, row - first)
            first = row
            current = next
        }
    }

    /**
     * How far row [row] of a [width] by [height] oval reaches either side of the oval's center, as
     * the distance to the center of its outermost pixel in half pixels; -1 for a row the oval misses
     * altogether.
     *
     * In half pixels because that keeps the test in whole numbers: a pixel's center is always a
     * whole number of half pixels from the center of a box, whatever the box. With u and v those
     * offsets for a pixel, it is inside when (u / width)² + (v / height)² <= 1, so the widest u a
     * row allows is the largest whole one with u² <= width² (height² - v²) / height².
     */
    @PublishedApi
    internal fun reach(width: Int, height: Int, row: Int): Int {
        if (row < 0 || row >= height) return -1
        val w = width.toLong()
        val h = height.toLong()
        val v = (2 * row + 1 - height).toLong()
        val bound = w * w * (h * h - v * v) / (h * h)
        var u = sqrt(bound.toDouble()).toInt()
        // The square root of a double is only nearly exact; settle it on the whole number.
        while (u.toLong() * u > bound) u--
        while ((u + 1).toLong() * (u + 1) <= bound) u++
        // A pixel's offset is odd across an even width and even across an odd one.
        if ((u + width) % 2 == 0) u--
        return u
    }

    /**
     * How far row [row] of the oval's [ovalInterior] reaches, in the terms of [reach]: short of the
     * row's own two ends, and no further than either neighboring row reaches.
     */
    @PublishedApi
    internal fun innerReach(width: Int, height: Int, row: Int): Int {
        val outer = reach(width, height, row)
        if (outer < 0) return -1
        val inner = minOf(outer - 2, reach(width, height, row - 1), reach(width, height, row + 1))
        return if (inner < 0) -1 else inner
    }
}
