package at.smiech.engine

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
    fun drawPixmap(pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int)
    fun drawPixmap(pixmap: Pixmap, x: Int, y: Int)

    /**
     * The same blit stretched into a [dstWidth] by [dstHeight] box, nearest-neighbour on both
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
     * The same blit again, turned [rotationDegrees] clockwise about the centre of its destination
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

/** The eight neighbours of the origin, as x/y pairs. */
private val OUTLINE_OFFSETS = intArrayOf(
    -1, -1, 0, -1, 1, -1,
    -1, 0, 1, 0,
    -1, 1, 0, 1, 1, 1,
)
