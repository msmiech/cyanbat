package at.smiech.engine

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Which pixels the [Graphics] shapes cover, computed in common code and handed out as rectangles,
 * so every backend fills exactly the same pixels.
 *
 * Platform shape primitives do not agree. Android's `Paint()` antialiases by default since Android
 * 12, and even aliased, Skia and Java2D disagree on which pixels an edge covers: each approximates
 * curves its own way, Java2D snaps paths to a grid of its own (leaving small ovals lopsided), and
 * Skia ends a line a pixel short. Filled rectangles they agree on exactly, so everything here comes
 * out as runs of them.
 *
 * Nothing is antialiased: a pixel is in a shape or not. No pixel is handed out twice for one shape,
 * so a translucent shape is blended evenly.
 */
object Raster {

    /**
     * The oval [Graphics.drawOval] fills: inscribed in the [width] by [height] box
     * at [x], [y], covering every pixel whose center lies inside it or on its edge.
     * Handed to [fill] top to bottom, one rectangle per run of equal-width rows,
     * since a backend pays more per rectangle than per pixel.
     *
     * Symmetric left to right and top to bottom for any box, which a pixel-art circle needs and
     * neither platform's own oval managed.
     */
    inline fun oval(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fill: (left: Int, top: Int, width: Int, height: Int) -> Unit,
    ) = rowRuns(x, y, width, height, { reach(width, height, it) }, fill)

    /**
     * The inside of the oval [oval] fills: its pixels whose four neighbors are all in the oval too.
     * Handed out as [oval] does.
     *
     * Every row is one unbroken run centered on the oval, so it traces as a single
     * polygon; a backend can fill [ovalOutline] in one draw call as the oval and
     * its inside together, filled even-odd.
     */
    inline fun ovalInterior(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fill: (left: Int, top: Int, width: Int, height: Int) -> Unit,
    ) = rowRuns(x, y, width, height, { innerReach(width, height, it) }, fill)

    /**
     * The one-pixel outline of the oval [oval] fills: its pixels minus its [ovalInterior]. One or
     * two runs per row, top to bottom.
     *
     * The oval's own rim rather than a stroke around it, so an outline drawn over the same filled
     * oval lands exactly on its edge.
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
     * for a steep line), the pixel nearest the true line, ties going toward the starting end.
     * Handed to [fill] as horizontal runs, or vertical ones for a steep line.
     *
     * Always walked from its left end (top, for a steep line), so a line covers the same pixels in
     * either direction.
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
            // Bresenham: how far past halfway to the next row the true line is at the next column,
            // scaled by 2 * dx to stay integral. Past halfway, that column steps over.
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
     * One rectangle per run of the box's rows that reach equally far either side of its center;
     * [reachOf] gives each row's reach, in the terms of [reach].
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
        // Runs to one past the last row, which reaches nowhere and so closes the last rectangle.
        for (row in 1..height) {
            val next = if (row < height) reachOf(row) else -1
            if (next == current) continue
            if (current >= 0) fill(
                x + (width - 1 - current) / 2,
                y + first,
                current + 1,
                row - first
            )
            first = row
            current = next
        }
    }

    /**
     * How far row [row] of a [width] by [height] oval reaches either side of its center: the
     * distance to its outermost pixel's center in half pixels, or -1 for a row the oval misses.
     *
     * Half pixels keep the test integral, since a pixel's center is always a whole number of half
     * pixels from any box's center. With u and v those offsets, a pixel is inside when
     * (u / width)² + (v / height)² <= 1, so a row's widest u is the largest integer with
     * u² <= width² (height² - v²) / height².
     */
    @PublishedApi
    internal fun reach(width: Int, height: Int, row: Int): Int {
        if (row < 0 || row >= height) return -1
        val w = width.toLong()
        val h = height.toLong()
        val v = (2 * row + 1 - height).toLong()
        val bound = w * w * (h * h - v * v) / (h * h)
        var u = sqrt(bound.toDouble()).toInt()
        // A double's square root is only nearly exact; settle on the integer.
        while (u.toLong() * u > bound) u--
        while ((u + 1).toLong() * (u + 1) <= bound) u++
        // A pixel's offset is odd across an even width and even across an odd one.
        if ((u + width) % 2 == 0) u--
        return u
    }

    /**
     * How far row [row] of the oval's [ovalInterior] reaches, in the terms of [reach]: inside the
     * row's own two ends, and no further than either neighboring row.
     */
    @PublishedApi
    internal fun innerReach(width: Int, height: Int, row: Int): Int {
        val outer = reach(width, height, row)
        if (outer < 0) return -1
        val inner = minOf(outer - 2, reach(width, height, row - 1), reach(width, height, row + 1))
        return if (inner < 0) -1 else inner
    }
}
