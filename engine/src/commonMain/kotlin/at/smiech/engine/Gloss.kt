package at.smiech.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where a sprite catches an outside light: a glint along the side the light comes from.
 *
 * The art has no surface to light, so one is derived from its outline: every creature and rock is
 * treated as rounded over the few pixels inside its edge and flat across the rest. A light glints
 * off the stretch of that rounded edge facing it, giving a thin rim of light a pixel or two inside
 * the outline on the lit side.
 *
 * Deliberately subtle: it shows which way the light falls and lifts a creature's outline off the
 * dark, without making anything look wet. It comes in [LEVELS] steps of brightness, like the rest
 * of the shading, and in [DIRECTIONS] directions rather than every angle, so a frame's glints can
 * be computed once and cached as pictures.
 */
object Gloss {
    /** How many directions a light can come from, evenly spaced round the circle. */
    const val DIRECTIONS = 16

    /** How many steps of brightness a glint comes in. */
    const val LEVELS = 3

    /** How far inside the outline a sprite rounds off, in pixels. */
    private const val BEVEL = 3f

    /**
     * A light's height above the frame's plane relative to its distance across it.
     * Low, so the light rakes across and the glint stays on the facing edge,
     * outlining it rather than sheening the middle.
     */
    private const val ELEVATION = 0.3f

    /** How tight the glint is: the higher, the narrower the stretch of edge that catches it. */
    private const val SHININESS = 16f

    /** The alpha from which a pixel counts as part of the picture rather than the air around it. */
    private const val OPAQUE_ALPHA = 128

    /**
     * The surface of a [width] by [height] block of ARGB [pixels]: a normal for each opaque pixel,
     * facing straight out across the flat middle and tilting outward toward the outline.
     */
    fun surface(pixels: IntArray, width: Int, height: Int): Surface {
        val opaque = BooleanArray(width * height) { pixels[it] ushr 24 >= OPAQUE_ALPHA }

        // Past the frame's edge the art is treated as continuing as it is at the edge. A creature's
        // frame has air all round it anyway; a rock's is cut off at the floor or roof, and that cut
        // is not an edge to glint off.
        fun solid(x: Int, y: Int) =
            opaque[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]

        // Each opaque pixel's distance from the air, center to center, up to a
        // little past the bevel.
        val reach = BEVEL.toInt() + 1
        val rise = FloatArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            if (!opaque[y * width + x]) continue
            var nearest = (reach * reach).toFloat()
            for (dy in -reach..reach) for (dx in -reach..reach) {
                if (solid(x + dx, y + dy)) continue
                nearest = minOf(nearest, (dx * dx + dy * dy).toFloat())
            }
            // A quarter circle in profile: steep at the outline, level by the time the bevel is crossed.
            val across = ((sqrt(nearest) - 0.5f) / BEVEL).coerceIn(0f, 1f)
            val inward = 1f - across
            rise[y * width + x] = BEVEL * sqrt(1f - inward * inward)
        }

        fun riseAt(x: Int, y: Int) =
            if (solid(x, y)) rise[y.coerceIn(0, height - 1) * width + x.coerceIn(
                0,
                width - 1
            )] else 0f

        // Averaged over neighbors, so each step of a pixel outline does not face a different way.
        val softened = FloatArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            if (!opaque[y * width + x]) continue
            var sum = 0f
            for (dy in -1..1) for (dx in -1..1) sum += riseAt(x + dx, y + dy)
            softened[y * width + x] = sum / 9f
        }
        fun heightAt(x: Int, y: Int) =
            if (solid(x, y)) softened[y.coerceIn(0, height - 1) * width + x.coerceIn(
                0,
                width - 1
            )] else 0f

        val nx = FloatArray(width * height)
        val ny = FloatArray(width * height)
        val nz = FloatArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            if (!opaque[i]) continue
            val slopeX = (heightAt(x + 1, y) - heightAt(x - 1, y)) / 2f
            val slopeY = (heightAt(x, y + 1) - heightAt(x, y - 1)) / 2f
            val length = sqrt(slopeX * slopeX + slopeY * slopeY + 1f)
            nx[i] = -slopeX / length
            ny[i] = -slopeY / length
            nz[i] = 1f / length
        }
        return Surface(width, height, opaque, nx, ny, nz)
    }

    /** A sprite frame's surface; see [surface]. */
    class Surface internal constructor(
        val width: Int,
        val height: Int,
        private val opaque: BooleanArray,
        private val nx: FloatArray,
        private val ny: FloatArray,
        private val nz: FloatArray,
    ) {
        /**
         * How brightly each pixel glints under a light from [direction], as a level
         * from 0 (none) to [LEVELS], row by row. Directions run from 0, a light to
         * the right, clockwise with y down.
         *
         * Blinn-Phong with the eye looking straight into the frame: the closer a pixel's normal is
         * to halfway between the light and the eye, the brighter. A pixel facing away gets none.
         */
        fun glints(direction: Int): ByteArray {
            val angle = direction * 2.0 * PI / DIRECTIONS
            val horizontal = 1f / sqrt(1f + ELEVATION * ELEVATION)
            val lightX = cos(angle).toFloat() * horizontal
            val lightY = sin(angle).toFloat() * horizontal
            val lightZ = ELEVATION * horizontal
            val halfLength = sqrt(lightX * lightX + lightY * lightY + (lightZ + 1f) * (lightZ + 1f))
            val halfX = lightX / halfLength
            val halfY = lightY / halfLength
            val halfZ = (lightZ + 1f) / halfLength

            val levels = ByteArray(width * height)
            for (i in levels.indices) {
                if (!opaque[i]) continue
                if (nx[i] * lightX + ny[i] * lightY + nz[i] * lightZ <= 0f) continue
                val facing = nx[i] * halfX + ny[i] * halfY + nz[i] * halfZ
                if (facing <= 0f) continue
                val level = (facing.pow(SHININESS) * LEVELS + 0.5f).toInt().coerceIn(0, LEVELS)
                levels[i] = level.toByte()
            }
            return levels
        }
    }
}
