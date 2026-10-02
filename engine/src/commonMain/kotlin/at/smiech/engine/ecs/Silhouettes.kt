package at.smiech.engine.ecs

import at.smiech.engine.FrameCache
import at.smiech.engine.Pixmap

/**
 * The outline a shadow is cast in, for each frame of the art: the convex hull of the frame's opaque
 * pixels, worked out from the pixels the first time the frame is asked for, and kept.
 *
 * A hull rather than the outline itself, because a convex shape throws its shadow from the two points
 * of it a light's rays graze and the far side between them, which is a handful of arithmetic a light.
 * A creature's wings and limbs are far from convex, but a shadow is read for where it falls rather
 * than for its every edge, and the hull of a wingbeat still beats.
 */
internal class Silhouettes {
    private var pixels = IntArray(0)
    private val hulls = FrameCache { pixmap, x, y, width, height, _ -> hull(pixmap, x, y, width, height) }

    /**
     * The hull of the [width] by [height] frame of [pixmap] at [x], [y], as x, y pairs of pixel corners
     * with (0, 0) the frame's top left, wound with a positive signed area; or null for a frame with
     * nothing opaque in it. A pixmap that has no pixels to read, as a test double has none, is taken
     * to fill its frame.
     */
    fun of(pixmap: Pixmap, x: Int, y: Int, width: Int, height: Int): FloatArray? = hulls[pixmap, x, y, width, height]

    private fun hull(pixmap: Pixmap, x: Int, y: Int, width: Int, height: Int): FloatArray? {
        if (pixels.size < width * height) pixels = IntArray(width * height)
        if (!pixmap.readPixels(pixels, x, y, width, height)) {
            val w = width.toFloat()
            val h = height.toFloat()
            return floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        }
        return ConvexHull.ofOpaque(pixels, width, height)
    }
}

/** Convex hulls, of the kind a shadow is cast from. */
internal object ConvexHull {

    /** The alpha from which a pixel counts as part of the picture rather than the air around it. */
    private const val OPAQUE_ALPHA = 128

    /**
     * The hull of every pixel of the [width] by [height] ARGB [pixels] that is at least half opaque, as
     * x, y pairs of pixel corners wound with a positive signed area; or null when none is.
     *
     * Only the first and last such pixel of each row can be on a hull of them, so it is those two's
     * outer corners that are wrapped.
     */
    fun ofOpaque(pixels: IntArray, width: Int, height: Int): FloatArray? {
        val corners = LongArray(4 * height)
        var count = 0
        for (row in 0 until height) {
            var first = -1
            var last = -1
            for (column in 0 until width) {
                if (pixels[row * width + column] ushr 24 >= OPAQUE_ALPHA) {
                    if (first < 0) first = column
                    last = column
                }
            }
            if (first < 0) continue
            corners[count++] = pack(first, row)
            corners[count++] = pack(first, row + 1)
            corners[count++] = pack(last + 1, row)
            corners[count++] = pack(last + 1, row + 1)
        }
        if (count == 0) return null
        return of(corners, count)
    }

    /**
     * The hull of the first [count] of [points], each an x and a y [pack]ed into one Long, by Andrew's
     * monotone chain: sorted by x and then y, a lower chain built left to right and an upper one back,
     * each dropping any point that would not turn it the same way as the rest. Points on a hull's edge
     * are dropped with the rest, so each corner is a real one.
     */
    fun of(points: LongArray, count: Int): FloatArray {
        points.sort(0, count)
        val hull = LongArray(2 * count)
        var size = 0
        for (i in 0 until count) {
            while (size >= 2 && turn(hull[size - 2], hull[size - 1], points[i]) <= 0L) size--
            hull[size++] = points[i]
        }
        val lower = size + 1
        for (i in count - 2 downTo 0) {
            while (size >= lower && turn(hull[size - 2], hull[size - 1], points[i]) <= 0L) size--
            hull[size++] = points[i]
        }
        // The upper chain ends back on the first point, which is already the lower chain's start.
        size--
        return FloatArray(2 * size) { i ->
            val point = hull[i / 2]
            if (i % 2 == 0) xOf(point).toFloat() else yOf(point).toFloat()
        }
    }

    /** Whether going from [a] through [b] to [c] turns one way (positive), the other, or not at all. */
    private fun turn(a: Long, b: Long, c: Long): Long =
        (xOf(b) - xOf(a)).toLong() * (yOf(c) - yOf(a)) - (yOf(b) - yOf(a)).toLong() * (xOf(c) - xOf(a))

    /** An x and a y in one Long that sorts by x and then by y; both are frame pixels, never negative. */
    fun pack(x: Int, y: Int): Long = (x.toLong() shl 32) or y.toLong()
    private fun xOf(point: Long): Int = (point ushr 32).toInt()
    private fun yOf(point: Long): Int = point.toInt()
}
