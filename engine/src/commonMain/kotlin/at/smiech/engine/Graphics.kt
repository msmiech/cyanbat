package at.smiech.engine

/**
 * What a screen draws its frame with, in integer frame pixels; the frame is the game's 640x360.
 *
 * Everything but text is pixel art and lands on the frame's pixel grid at any display size; text is
 * drawn smooth at the screen's resolution. `ComposeGraphics` is the one real implementation, for
 * every platform; the others are test doubles.
 */
interface Graphics {
    /** Loads the image asset [filename]. */
    fun newPixmap(filename: String): Pixmap

    /** Fills the whole frame with [color], starting a new frame. */
    fun clear(color: Int)
    fun drawPixel(x: Int, y: Int, color: Int)

    /**
     * A line one pixel wide between the two points, both ends included; [Raster.line] picks the
     * pixels.
     *
     * Laid down as runs through [drawRect], so it is the same pixels on every backend. A backend
     * must not use a line primitive of its own, since Skia's and Java2D's end on different pixels,
     * though a test double that records lines may override it.
     */
    fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int) =
        Raster.line(xFrom, yFrom, xTo, yTo) { left, top, w, h -> drawRect(left, top, w, h, color) }

    fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int)

    /**
     * The filled oval inscribed in the [width] by [height] box at [x], [y]; [Raster.oval] picks the
     * pixels.
     *
     * Laid down as rows through [drawRect], for the same reason as [drawLine]. A backend may fill
     * the same pixels faster, but never with an oval primitive of its own.
     */
    fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int) =
        Raster.oval(x, y, width, height) { left, top, w, h -> drawRect(left, top, w, h, color) }

    /**
     * The one-pixel rim of the oval [drawOval] would fill, so an outline drawn over the same filled
     * oval lands exactly on its edge. [Raster.ovalOutline] picks the pixels.
     */
    fun drawOvalOutline(x: Int, y: Int, width: Int, height: Int, color: Int) =
        Raster.ovalOutline(x, y, width, height) { left, top, w, h ->
            drawRect(
                left,
                top,
                w,
                h,
                color
            )
        }

    /**
     * Draws the [srcWidth] by [srcHeight] region at [srcX], [srcY] of [pixmap] with
     * its corner at [x], [y].
     */
    fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int
    )

    /** Draws the whole of [pixmap] with its corner at [x], [y]. */
    fun drawPixmap(pixmap: Pixmap, x: Int, y: Int)

    /**
     * The plain blit drawn over what is already there at [alpha] opacity, 0..1.
     *
     * This is how crossfades work: one picture drawn, then another of the same shape over it at
     * [alpha], lands exactly that far between the two. The desert's strips change time of day this
     * way, with no palette computed at run time.
     *
     * The default, for test doubles, shows the picture outright once it is at least half faded in.
     * The real backend overrides it with a true blend.
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
     * The blit stretched into a [dstWidth] by [dstHeight] box, nearest-neighbor so a magnified
     * sprite stays pixel art.
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
     * The scaled blit, turned [rotationDegrees] clockwise about the center of its destination box.
     * The box itself does not move or grow.
     *
     * A separate method rather than a parameter because nearly every sprite is drawn unrotated, and
     * that path never touches the canvas transform.
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
     * The sprite's shape filled flat with [color], drawn over what is already there.
     *
     * The pixmap's alpha is the mask, and [color]'s alpha is how strongly the fill shows. Drawn
     * over a normal blit of the same frame, it lights the sprite up within its outline, which is
     * how a hit flash reads as the sprite flashing rather than a rectangle over it.
     *
     * A separate method rather than a tint parameter on the blits, so ordinary sprites pay no paint
     * setup for an effect that lasts a tenth of a second.
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
     * [drawPixmapSilhouette] turned [rotationDegrees] clockwise about the center of its box, as the
     * rotated [drawPixmap] turns a sprite, so a flash on a turned sprite matches its shape.
     *
     * The default, for test doubles, draws the silhouette upright.
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
    ) = drawPixmapSilhouette(
        pixmap,
        x,
        y,
        srcX,
        srcY,
        srcWidth,
        srcHeight,
        dstWidth,
        dstHeight,
        color
    )

    /**
     * Lights everything drawn so far with [lighting]. Each pixel is multiplied by the light
     * reaching it ([Lighting.ambient], tinted toward every light whose disc it lies in and whose
     * shadows it is outside of), that light is added back at [Lighting.glow], and the glints are
     * added over their sprites. Anything drawn afterwards is left as drawn, which is how lights
     * stay bright in the dark.
     *
     * The light is computed per cell of frame pixels, so it lands on the frame's grid; the glints
     * per frame pixel, like the sprites they belong to.
     *
     * The default, for test doubles, lights nothing.
     */
    fun drawLighting(lighting: Lighting) {}

    /** [s] in the platform's sans-serif face, [fontSize] frame pixels tall, from its baseline at [x], [y]. */
    fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int)

    /**
     * Draws [s] with a one-pixel outline in [outlineColor], so it stays readable
     * over any background.
     *
     * The default, for test doubles, stamps the string at the eight surrounding pixels in the
     * outline color and then draws the fill; four would leave a glyph's diagonals bare. The real
     * backend strokes the glyphs instead, which is a clean line at the screen's resolution.
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
            drawString(
                s,
                x + OUTLINE_OFFSETS[i],
                y + OUTLINE_OFFSETS[i + 1],
                fontSize,
                outlineColor
            )
            i += 2
        }
        drawString(s, x, y, fontSize, color)
    }

    /**
     * The width [drawString] gives [s] at [fontSize], in frame pixels, rounded up so text placed by
     * it never runs past the edge it is aligned to.
     *
     * Each platform draws its own sans-serif face (Roboto on Android, Arial on
     * Windows, usually DejaVu Sans on Linux), and widths differ: the HUD's "Level:
     * 17" is 58 px in Arial and 67 px in DejaVu Sans. Right-aligned, centered or
     * wrapped text must be measured, never counted in characters.
     */
    fun measureString(s: String, fontSize: Int): Int

    /** The frame's size in pixels. */
    val width: Int
    val height: Int
}

/** The eight neighbors of the origin, as x/y pairs. */
private val OUTLINE_OFFSETS = intArrayOf(
    -1, -1, 0, -1, 1, -1,
    -1, 0, 1, 0,
    -1, 1, 0, 1, 1, 1,
)
