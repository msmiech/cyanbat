package at.smiech.engine.ecs

import at.smiech.engine.FrameCache
import at.smiech.engine.Pixmap

/**
 * The outline each frame of the art casts its shadow from: the convex hull of the frame's opaque
 * pixels, computed on first use and cached.
 *
 * A hull rather than the exact outline, because a convex shape's shadow is just the two points the
 * light's rays graze and the far side between them, a handful of arithmetic per light. Wings and
 * limbs are far from convex, but a shadow is read for where it falls, and the hull of a wingbeat
 * still beats.
 */
internal class Silhouettes {
    private var pixels = IntArray(0)
    private val hulls =
        FrameCache { pixmap, x, y, width, height, _ -> hull(pixmap, x, y, width, height) }

    /**
     * The hull of the [width] by [height] frame of [pixmap] at [x], [y], as x, y
     * pairs of pixel corners relative to the frame's top left, wound with a
     * positive signed area; null for a frame with nothing opaque. A pixmap with no
     * readable pixels, such as a test double, fills its frame.
     */
    fun of(pixmap: Pixmap, x: Int, y: Int, width: Int, height: Int): FloatArray? =
        hulls[pixmap, x, y, width, height]

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

/** Convex hulls of sprite frames, which shadows are cast from. */
internal object ConvexHull {

    /** The alpha from which a pixel counts as part of the picture rather than the air around it. */
    private const val OPAQUE_ALPHA = 128

    /**
     * The hull of every at least half-opaque pixel of the [width] by [height] ARGB [pixels], as x,
     * y pairs of pixel corners wound with a positive signed area; null when there are none.
     *
     * Only the first and last such pixel of each row can be on the hull, so only
     * their outer corners are wrapped.
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
     * The hull of the first [count] of [points], each an x and a y [pack]ed into one Long, by
     * Andrew's monotone chain: sorted by x then y, a lower chain built left to right and an upper
     * one back, each dropping points that do not turn the same way. Collinear points are dropped
     * too, so every corner is a real one.
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

    /**
     * The cross product of a to b and a to c: positive for one turn direction,
     * negative for the other, zero for none.
     */
    private fun turn(a: Long, b: Long, c: Long): Long =
        (xOf(b) - xOf(a)).toLong() * (yOf(c) - yOf(a)) - (yOf(b) - yOf(a)).toLong() * (xOf(c) - xOf(
            a
        ))

    /** An x and a y in one Long that sorts by x, then y; both are non-negative frame pixels. */
    fun pack(x: Int, y: Int): Long = (x.toLong() shl 32) or y.toLong()
    private fun xOf(point: Long): Int = (point ushr 32).toInt()
    private fun yOf(point: Long): Int = point.toInt()
}
