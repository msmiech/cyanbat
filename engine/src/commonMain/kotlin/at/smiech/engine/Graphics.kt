package at.smiech.engine

import kotlin.math.cos
import kotlin.math.sin

interface Graphics {
    enum class PixmapFormat {
        ARGB8888, RGB565
    }

    fun newPixmap(filename: String, format: PixmapFormat): Pixmap
    fun clear(color: Int)
    fun drawPixel(x: Int, y: Int, color: Int)
    fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int)
    fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int)
    fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int)

    /**
     * The outline of the oval [drawOval] would fill, one pixel thick.
     *
     * A default rather than an abstract method, so a test double that only draws rectangles does
     * not have to learn it: this plots the ring pixel by pixel, which is correct everywhere and
     * slow everywhere. Both real backends override it with their own stroked oval.
     */
    fun drawOvalOutline(x: Int, y: Int, width: Int, height: Int, color: Int) {
        val rx = width / 2f
        val ry = height / 2f
        if (rx <= 0f || ry <= 0f) return
        val cx = x + rx
        val cy = y + ry
        // Enough steps that neighbouring samples are never more than a pixel apart.
        val steps = (maxOf(rx, ry) * 7f).toInt().coerceAtLeast(8)
        for (i in 0 until steps) {
            val angle = i * 6.2831855f / steps
            drawPixel(
                (cx + rx * cos(angle)).toInt(),
                (cy + ry * sin(angle)).toInt(),
                color
            )
        }
    }
    fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int
    )

    fun drawPixmap(pixmap: Pixmap, x: Int, y: Int)

    /**
     * The same blit stretched into a [dstWidth] by [dstHeight] box, nearest-neighbor on both
     * platforms so a magnified sprite stays pixel art instead of turning to mush.
     *
     * Both backends already blit through a destination rectangle, so this costs a scaled sprite
     * nothing over an unscaled one.
     */
    fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
    )

    /**
     * The same blit again, turned [rotationDegrees] clockwise about the center of its destination
     * box. The box itself does not move or grow: a rotated sprite occupies the same place on
     * screen, pointing a different way.
     *
     * Kept as its own method rather than a parameter on the others because the unrotated blit is
     * every sprite in the game bar the projectiles, and both backends draw it without having to
     * touch the canvas transform at all.
     */
    fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        rotationDegrees: Float,
    )

    /**
     * The sprite's own shape, filled flat with [color], drawn over whatever is already there.
     *
     * The pixmap supplies the silhouette and nothing else: its alpha is the mask, and [color]'s
     * alpha is how strongly the fill shows through. Painted over a normal blit of the same frame
     * at the same place, this lights the sprite up without touching its outline - which is what a
     * hit needs to read as the thing that was hit flashing, rather than as a rectangle over it.
     *
     * Its own method rather than a tint parameter on the blits above, because every sprite in the
     * game is drawn by those and none of them should pay a paint setup for something that happens
     * to one enemy for a tenth of a second.
     */
    fun drawPixmapSilhouette(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        color: Int,
    )

    fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int)
    val width: Int
    val height: Int
}

/**
 * Draws [s] ringed by a one-pixel outline in [outlineColor], so it stays readable over whatever
 * the game happens to be drawing behind it.
 *
 * Neither platform's text API outlines, so the outline is the same string stamped around the fill.
 * Eight offsets rather than four: a four-way ring leaves the diagonals of a glyph bare.
 */
fun Graphics.drawOutlinedString(
    s: String,
    x: Int,
    y: Int,
    fontSize: Int,
    color: Int,
    outlineColor: Int = EngineColors.BLACK,
) {
    var i = 0
    while (i < OUTLINE_OFFSETS.size) {
        drawString(s, x + OUTLINE_OFFSETS[i], y + OUTLINE_OFFSETS[i + 1], fontSize, outlineColor)
        i += 2
    }
    drawString(s, x, y, fontSize, color)
}

/** The eight neighbors of the origin, as x/y pairs. */
private val OUTLINE_OFFSETS = intArrayOf(
    -1, -1, 0, -1, 1, -1,
    -1, 0, 1, 0,
    -1, 1, 0, 1, 1, 1,
)
