package at.smiech.engine

/**
 * What a screen draws its frame with: integer coordinates in frame pixels, the frame being the
 * game's 640x360.
 *
 * Everything but text is pixel art, and lands on the frame's pixel grid however large the frame is
 * shown; text is drawn smooth at the screen's resolution. `ComposeGraphics` is the one real
 * implementation, for every platform; the rest are test doubles.
 */
interface Graphics {
    enum class PixmapFormat {
        ARGB8888, RGB565
    }

    fun newPixmap(filename: String, format: PixmapFormat): Pixmap
    fun clear(color: Int)
    fun drawPixel(x: Int, y: Int, color: Int)

    /**
     * A line one pixel wide from one point to the other, both ends included; [Raster.line] says
     * which pixels.
     *
     * Laid down as runs through [drawRect], which is what makes it the same pixels on every
     * backend. A backend must not draw it with a line of its own - Skia's and Java2D's end on
     * different pixels - though a test double that records lines can override it.
     */
    fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int) =
        Raster.line(xFrom, yFrom, xTo, yTo) { left, top, w, h -> drawRect(left, top, w, h, color) }

    fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int)

    /**
     * The oval inscribed in the [width] by [height] box at [x], [y], filled; [Raster.oval] says
     * which pixels.
     *
     * Laid down as runs of rows through [drawRect], for the reason [drawLine] is. A backend may fill
     * the same pixels a faster way, as Android does, but never with an oval of its own.
     */
    fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int) =
        Raster.oval(x, y, width, height) { left, top, w, h -> drawRect(left, top, w, h, color) }

    /**
     * The outline of the oval [drawOval] would fill, one pixel thick: the fill's own rim, so an
     * outline drawn over the same oval filled lands exactly on its edge. [Raster.ovalOutline] says
     * which pixels, and it is laid down like the fill.
     */
    fun drawOvalOutline(x: Int, y: Int, width: Int, height: Int, color: Int) =
        Raster.ovalOutline(x, y, width, height) { left, top, w, h -> drawRect(left, top, w, h, color) }

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
     * The plain blit laid over whatever is already there at [alpha] of its opacity, where alpha runs
     * 0..1, so what was drawn before still shows through.
     *
     * What a crossfade is made of. One picture drawn, and then another of the same shape over it at
     * [alpha], lands exactly that far between the two - which is how a desert strip drawn in the
     * palettes of several times of day turns from one into the next without a palette ever being
     * computed at run time.
     *
     * A default rather than an abstract method, so a test double does not have to learn it: this
     * shows the picture outright once it is at least half faded in, which is right at both ends and
     * wrong in between. Both real backends override it with a true blend.
     */
    fun drawPixmapFaded(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        alpha: Float,
    ) {
        if (alpha >= 0.5f) drawPixmap(pixmap, x, y, srcX, srcY, srcWidth, srcHeight)
    }

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

    /**
     * [drawPixmapSilhouette] turned [rotationDegrees] clockwise about the center of its box, the way
     * the rotated [drawPixmap] turns a sprite: a flash on a sprite that is itself drawn turned has
     * to be turned with it, or it lights up a shape the sprite is not.
     *
     * Defaults to the upright silhouette, which is all a test double needs. Both backends turn it.
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
        rotationDegrees: Float,
    ) = drawPixmapSilhouette(pixmap, x, y, srcX, srcY, srcWidth, srcHeight, dstWidth, dstHeight, color)

    /**
     * Lights everything drawn so far with [lighting]: each pixel of the frame multiplied by the light
     * that reaches it - [Lighting.ambient], taken toward the color of every light whose disc it lies in
     * and out of whose shadows it is - and then that light added over it at [Lighting.glow], and the
     * glints added over the sprites they belong to. What is drawn after this is left as it is drawn,
     * which is how something that is itself a light stays bright in the dark.
     *
     * The light is worked out a cell of frame pixels at a time, so it lands on the frame's grid like
     * everything else; the glints a frame pixel at a time, like the sprites they belong to.
     *
     * A default that lights nothing, so a test double does not have to learn it.
     */
    fun drawLighting(lighting: Lighting) {}

    /** [s] in the platform's sans-serif face, [fontSize] frame pixels tall, from its baseline at [x], [y]. */
    fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int)

    /**
     * Draws [s] ringed by an outline a frame pixel thick in [outlineColor], so it stays readable over
     * whatever the game happens to be drawing behind it.
     *
     * A default rather than an abstract method, so a test double does not have to learn it: this
     * stamps the string at the eight pixels around it in the outline color and then draws the fill,
     * eight rather than four because a four-way ring leaves the diagonals of a glyph bare. The real
     * backend strokes the glyphs instead, which at the screen's resolution is a clean line where
     * eight stamps would be eight copies.
     */
    fun drawOutlinedString(
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

    /**
     * The width [drawString] gives [s] at [fontSize], in frame pixels. Rounded up, so that text
     * placed by it never runs past the edge it was placed against.
     *
     * Text is the one thing that does not come out the same everywhere. It is drawn in each
     * platform's own sans-serif face - Roboto on Android; on the desktop whatever face Compose finds
     * for sans-serif, Arial on Windows - and the faces disagree on widths: the HUD's "Level: 17" is
     * 58px wide in Arial and 67px in DejaVu Sans. So text that is aligned by its right edge,
     * centered or wrapped has to be measured, never counted out in characters.
     */
    fun measureString(s: String, fontSize: Int): Int
    val width: Int
    val height: Int
}

/** The eight neighbors of the origin, as x/y pairs. */
private val OUTLINE_OFFSETS = intArrayOf(
    -1, -1, 0, -1, 1, -1,
    -1, 0, 1, 0,
    -1, 1, 0, 1, 1, 1,
)
