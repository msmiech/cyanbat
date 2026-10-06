package at.smiech.engine

import kotlin.math.roundToInt

/**
 * Which pixels of a picture to keep so that a given share of it shows, as an ordered dither: the
 * pixels left out are spread evenly in a fixed four by four pattern (a Bayer matrix), so the
 * picture reads as see-through while every pixel that is drawn is whole and on the grid. Blending
 * it instead would soften the pixel art into a color it was never drawn in.
 *
 * Each level keeps every pixel the level below it keeps, and one more of each sixteen, so stepping
 * through the levels dissolves a picture in or out a pixel at a time rather than flickering between
 * unrelated patterns.
 */
object Dither {
    /** How many steps there are between nothing and the whole picture. */
    const val LEVELS = 16

    /** How many pixels across and down the pattern repeats in. */
    const val TILE = 4

    /** The order the pixels of each tile come in, a row at a time. */
    private val ORDER = intArrayOf(
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5,
    )

    /** The level nearest [coverage], the share of the picture to show, 0..1. */
    fun level(coverage: Float): Int = (coverage.coerceIn(0f, 1f) * LEVELS).roundToInt()

    /**
     * Whether the pixel at [x], [y] of a picture shows at [level]: none at 0, all at [LEVELS]. Read
     * in the picture's own pixels, so the pattern moves with what it is drawn on.
     */
    fun keeps(x: Int, y: Int, level: Int): Boolean =
        ORDER[y.mod(TILE) * TILE + x.mod(TILE)] < level
}
