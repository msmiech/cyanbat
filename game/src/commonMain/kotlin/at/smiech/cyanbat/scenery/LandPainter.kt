package at.smiech.cyanbat.scenery

import at.smiech.cyanbat.scenery.ScenePalettes.BODY
import at.smiech.cyanbat.scenery.ScenePalettes.DEEP
import at.smiech.cyanbat.scenery.ScenePalettes.LEFT
import at.smiech.cyanbat.scenery.ScenePalettes.RIGHT
import at.smiech.cyanbat.scenery.ScenePalettes.TOP
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One band of the endless stage's land, back to front: where it sits on the 640x360 frame, how
 * tall it is, and how fast it scrolls, in frame pixels a tick like every velocity in the game. The
 * nearest moves with the obstacles standing on it; farther ones move slower, which reads as depth.
 *
 * @param floor ground under every land's own profile, in pixels, so a band reaches down behind the
 *   one in front of it and no sky shows between them.
 */
enum class Band(
    val top: Int,
    val height: Int,
    val speed: Float,
    internal val depth: ScenePalettes.Depth,
    val floor: Int = 0,
) {
    CLOUDS(top = 4, height = 130, speed = 0.07f, depth = ScenePalettes.Depth.CLOUDS),
    FAR(top = 70, height = 190, speed = 0.16f, depth = ScenePalettes.Depth.FAR),
    MID(top = 150, height = 200, speed = 0.42f, depth = ScenePalettes.Depth.MID, floor = 30),
    NEAR(top = 290, height = 70, speed = 1f, depth = ScenePalettes.Depth.NEAR),
}

/** Something in a chunk of land that the scenery animates where it stands: a volcano's crater. */
class Feature(val x: Float, val y: Float)

/**
 * A canvas for one chunk of one band of land, [width] columns from band-x [left], as palette codes
 * (see [ScenePalettes.code]). Coordinates are the band's own: x along the band from the run's
 * start, y down from the band's top. Whatever falls outside the chunk is clipped, so a tree
 * straddling two chunks is painted whole into both and meets itself at the join.
 */
internal class LandPainter(
    val band: Band,
    val left: Int,
    val width: Int,
    val height: Int = band.height
) {
    val codes = IntArray(width * height)
    val features = ArrayList<Feature>()

    operator fun contains(x: Int): Boolean = x - left in 0 until width

    fun get(x: Int, y: Int): Int {
        val cx = x - left
        return if (cx !in 0 until width || y !in 0 until height) 0 else codes[y * width + cx]
    }

    fun set(x: Int, y: Int, code: Int) {
        val cx = x - left
        if (cx !in 0 until width || y !in 0 until height) return
        codes[y * width + cx] = code
    }

    /** Sets [x], [y] only where nothing has been painted yet, for what stands behind. */
    fun under(x: Int, y: Int, code: Int) {
        if (get(x, y) == 0) set(x, y, code)
    }

    /**
     * A filled ellipse round [cx], [cy], each pixel coded by [codeAt] from where in it the pixel
     * is, -1..1 across and down.
     */
    inline fun ellipse(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        codeAt: (dx: Float, dy: Float) -> Int
    ) {
        if (rx <= 0f || ry <= 0f) return
        val x0 = floor(cx - rx).toInt()
        val x1 = ceil(cx + rx).toInt()
        val y0 = floor(cy - ry).toInt().coerceAtLeast(0)
        val y1 = ceil(cy + ry).toInt().coerceAtMost(height - 1)
        for (x in maxOf(x0, left) until minOf(x1 + 1, left + width)) {
            val dx = (x + 0.5f - cx) / rx
            if (dx * dx > 1f) continue
            for (y in y0..y1) {
                val dy = (y + 0.5f - cy) / ry
                if (dx * dx + dy * dy > 1f) continue
                val code = codeAt(dx, dy)
                if (code != 0) set(x, y, code)
            }
        }
    }

    /** A line one pixel wide, both ends included. */
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, code: Int) {
        val steps = maxOf(abs(x1 - x0), abs(y1 - y0)).roundToInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            set((x0 + (x1 - x0) * t).roundToInt(), (y0 + (y1 - y0) * t).roundToInt(), code)
        }
    }

    /** A rectangle, edges included. */
    fun rect(x0: Int, y0: Int, x1: Int, y1: Int, code: (x: Int, y: Int) -> Int) {
        for (x in maxOf(x0, left)..minOf(x1, left + width - 1)) {
            for (y in maxOf(y0, 0)..minOf(y1, height - 1)) set(x, y, code(x, y))
        }
    }

    companion object {
        /**
         * Which way a surface faces from its [slope], the rise of its height across two pixels: up
         * and to the left when it climbs to the right, up and to the right when it falls.
         */
        fun facing(slope: Float): Int = when {
            slope > SLOPE_SIDE -> LEFT
            slope < -SLOPE_SIDE -> RIGHT
            else -> TOP
        }

        /** How a pixel at [dx], [dy] (each -1..1) in a round mass faces: lit on the side it turns to. */
        fun roundShade(dx: Float, dy: Float): Int = when {
            dy > 0.55f -> DEEP
            dy < -0.55f && abs(dx) < 0.6f -> TOP
            dx < -0.45f -> LEFT
            dx > 0.45f -> RIGHT
            dy < -0.2f -> TOP
            else -> BODY
        }

        /** How a pixel at [dx] across a tall thin mass, -1..1, faces. */
        fun sideShade(dx: Float): Int = when {
            dx < -0.4f -> LEFT
            dx > 0.4f -> RIGHT
            else -> BODY
        }

        /** The distance from [x] to the middle of the cell [x] is in, for regular shapes. */
        fun sqrtSafe(v: Float): Float = if (v <= 0f) 0f else sqrt(v)

        private const val SLOPE_SIDE = 0.9f
    }
}
