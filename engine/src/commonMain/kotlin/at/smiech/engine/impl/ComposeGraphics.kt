package at.smiech.engine.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import at.smiech.engine.FrameCache
import at.smiech.engine.Gloss
import at.smiech.engine.Graphics
import at.smiech.engine.Lighting
import at.smiech.engine.Pixmap
import at.smiech.engine.Raster
import at.smiech.engine.impl.ComposeGraphics.Companion.LIGHT_CELL
import at.smiech.engine.impl.ComposeGraphics.Companion.MAX_TINTS
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The [Graphics] every platform draws the game with, through Compose's own Canvas API: HWUI, and so
 * the GPU, on Android; Skia on the desktop and on iOS.
 *
 * A screen's present is recorded here, call by call, and the host then draws the recording into its
 * Compose Canvas with [drawGameFrame]. Recorded rather than drawn on the spot, because the game loop
 * runs in Compose's frame callback and the drawing happens later, in its draw phase; and because one
 * frame is drawn more than once - into the window, and into a small picture the [AmbientBars] read
 * the frame's edges from, or into a whole one for the recorder and the tests ([drawInto]).
 *
 * Two grids are at work, which by default are one:
 * - The frame, [width] by [height], is what the game is laid out and played in. Every call arrives
 *   in its pixels, and the host scales it to the screen whole.
 * - The pixel grid is what the pixel art is drawn on: [gridScale] grid pixels to a frame pixel,
 *   each way. At the default of 1 it is the frame itself, and the game looks as it always has.
 *
 * Everything but text lands on the grid at any screen size, because it is drawn aliased and
 * nearest-neighbor under one scale. Sprites and rectangles are whole frame pixels, which are whole
 * grid pixels at any grid, so they magnify into blocks. Ovals are worked out on the grid ([Raster]),
 * and a turned sprite is turned on it before it is drawn ([turn]): turned at the screen's resolution
 * instead, its pixels would come out as squares tilted against the grid and finer than it.
 *
 * Text is the exception, by choice. It is laid out at its size in frame pixels, so the game places
 * it as it always has, but drawn at the screen's resolution and antialiased, so the HUD reads crisp
 * over the pixel art. An outline is a stroke around the glyphs.
 *
 * @param loadImage decodes an asset. The host knows where its assets are and how its platform
 *   decodes them, and the desktop has to do one thing more; see `DesktopGame`.
 * @param fontFamilyResolver how the platform finds a font.
 */
class ComposeGraphics(
    override val width: Int,
    override val height: Int,
    private val loadImage: (filename: String) -> ImageBitmap,
    fontFamilyResolver: FontFamily.Resolver,
) : Graphics {

    /**
     * How many grid pixels the pixel art is drawn with to a frame pixel, each way; 1, the default,
     * draws it on the frame's own pixels. Taken up at the start of the next frame, so a frame is
     * never drawn on two grids.
     *
     * Whole numbers only, so that a sprite's pixels and a rectangle's edges, which fall on frame
     * pixels, fall on grid pixels too.
     *
     * A finer grid changes only what is worked out on it: an oval keeps its box, and an outline its
     * weight of one frame pixel, with finer steps round the curve; a turned sprite keeps its pixels'
     * size, with finer steps along its edges. A line stays on the frame's grid for now, since its
     * weight is a frame pixel and [Raster] only lays down lines one grid pixel wide.
     */
    var gridScale: Int = 1
        set(value) {
            require(value >= 1) { "A grid scale is a whole number of grid pixels to a frame pixel, at least 1: $value" }
            field = value
        }

    /** The grid the frame being recorded is drawn on: [gridScale] as of its start. */
    private var grid = 1

    /**
     * Lays text out at density 1, which makes a font size in sp a size in frame pixels. A cache
     * larger than the default eight, because the HUD alone lays out more strings than that every
     * frame, and each one is measured where it is placed and again where it is drawn.
     */
    private val textMeasurer =
        TextMeasurer(fontFamilyResolver, FRAME_DENSITY, LayoutDirection.Ltr, TEXT_CACHE_SIZE)
    private val styles = HashMap<Int, TextStyle>()

    /**
     * The frame as recorded: each command an opcode and its arguments in [ints], a fade's alpha in
     * [floats], and the picture or the string it draws in [refs]. Kept from frame to frame and only
     * ever rewound, so that recording one allocates nothing.
     */
    private var ints = IntArray(1024)
    private var intCount = 0
    private var floats = FloatArray(64)
    private var floatCount = 0
    private val refs = ArrayList<Any>(256)

    /** The turned sprites, one for each a frame draws; see [turn]. */
    private val turns = ArrayList<Turn>()
    private var turnsUsed = 0

    // Every paint is aliased: an antialiased edge, scaled up, would soften the grid it is meant to
    // land on. Compose's Paint() is antialiased unless told otherwise.
    private val shapePaint = Paint().apply { isAntiAlias = false }
    private val imagePaint =
        Paint().apply { isAntiAlias = false; filterQuality = FilterQuality.None }
    private val fadePaint =
        Paint().apply { isAntiAlias = false; filterQuality = FilterQuality.None }
    private val silhouettePaint =
        Paint().apply { isAntiAlias = false; filterQuality = FilterQuality.None }
    private var silhouetteRgb = 0
    private val erasePaint = Paint().apply { blendMode = BlendMode.Clear }

    /**
     * Where an oval is traced as one polygon, and the right-hand ends of the runs traced so far: an
     * x, a top and a bottom for each. Kept from shape to shape, so that drawing one allocates
     * nothing.
     */
    private val staircase = Path()
    private var stepEnds = IntArray(3 * 128)
    private var steps = 0

    private val offscreen = CanvasDrawScope()

    /**
     * The frame's light as [drawLighting] last worked it out - a picture of [LIGHT_CELL] by
     * [LIGHT_CELL] frame pixels to its every pixel, drawn on the CPU - and which [Lighting], at which
     * [Lighting.version], it holds, so that a light that has not changed since is not drawn again.
     */
    private var lightImage: ImageBitmap? = null
    private var lightCanvas: Canvas? = null
    private var litBy: Lighting? = null
    private var litVersion = 0

    /** Every light asked for so far, by radius and then color, drawn once in its rings; see [lightSprite]. */
    private var lightSprites = arrayOfNulls<ArrayList<TintedLight>>(64)

    /** The first lights of frames so far, each already laid over the dark; see [darkLightSprite]. */
    private val darkLights = ArrayList<DarkLight>(2)

    /** Where a light that throws shadows has them cut out of it; see [shadowed]. */
    private var shadowScratch: ImageBitmap? = null
    private var shadowCanvas: Canvas? = null
    private val shadowPath = Path()

    // The light is drawn into pictures of whole cells of frame pixels, so whatever is aliased here
    // lands on the frame's grid: a shadow's edge falls between two cells, never across one. Every draw
    // into them is a plain copy or laid over what is there, the two blits Skia does fastest on the CPU
    // on every platform; adding light, or tinting it as it is drawn, costs several times as much a pixel.
    private val ambientPaint = Paint().apply { isAntiAlias = false; blendMode = BlendMode.Src }
    private val ringPaint = Paint().apply { isAntiAlias = false; blendMode = BlendMode.Src }
    private val copyPaint = Paint().apply {
        isAntiAlias = false; blendMode = BlendMode.Src; filterQuality = FilterQuality.None
    }
    private val shadowPaint = Paint().apply { isAntiAlias = false; blendMode = BlendMode.Clear }
    private val overPaint = Paint().apply {
        isAntiAlias = false; blendMode = BlendMode.SrcOver; filterQuality = FilterQuality.None
    }
    private val lightPaint = Paint().apply {
        isAntiAlias = false; blendMode = BlendMode.Modulate; filterQuality = FilterQuality.None
    }
    private val glowPaint = Paint().apply {
        isAntiAlias = false; blendMode = BlendMode.Plus; filterQuality = FilterQuality.None
    }

    /**
     * How each frame of the art glints, in each direction a light can come from: a picture the frame's
     * size, white where it catches the light and as opaque as the glint is bright, or null where it
     * catches none. Worked out from the frame's own pixels the first time it is asked for; see [Gloss].
     */
    private var framePixels = IntArray(0)
    private val surfaces = FrameCache { pixmap, x, y, width, height, _ ->
        if (framePixels.size < width * height) framePixels = IntArray(width * height)
        if (pixmap.readPixels(framePixels, x, y, width, height)) Gloss.surface(
            framePixels,
            width,
            height
        ) else null
    }
    private val glintMasks =
        FrameCache(Gloss.DIRECTIONS) { pixmap, x, y, width, height, direction ->
            surfaces[pixmap, x, y, width, height]?.let { glintMask(it, direction) }
        }
    private val maskPaint = Paint().apply { isAntiAlias = false; blendMode = BlendMode.Src }
    private val glintPaint = Paint().apply {
        isAntiAlias = false; blendMode = BlendMode.Plus; filterQuality = FilterQuality.None
    }

    /** A light's color, as the filter its glints are tinted with, one for each color seen; see [tint]. */
    private val tintColors = IntArray(MAX_TINTS)
    private val tintFilters = arrayOfNulls<ColorFilter>(MAX_TINTS)
    private var tintCount = 0

    /** Which slot the next new tint takes once every one is in use: the oldest. */
    private var nextTint = 0

    override fun newPixmap(filename: String, format: Graphics.PixmapFormat): Pixmap =
        ImagePixmap(loadImage(filename))

    /**
     * Starts the recording over, since nothing recorded before a clear could show through it. Every
     * screen clears before it draws, and that is what keeps the recording one frame long - and what
     * starts a frame on the grid [gridScale] asks for.
     */
    override fun clear(color: Int) {
        intCount = 0
        floatCount = 0
        refs.clear()
        turnsUsed = 0
        grid = gridScale
        shape(RECT, 0, 0, width, height, color or OPAQUE)
    }

    override fun drawPixel(x: Int, y: Int, color: Int) = shape(RECT, x, y, 1, 1, color)

    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        if (width > 0 && height > 0) shape(RECT, x, y, width, height, color)
    }

    override fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int) =
        shape(LINE, xFrom, yFrom, xTo, yTo, color)

    override fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int) =
        shape(OVAL, x, y, width, height, color)

    override fun drawOvalOutline(x: Int, y: Int, width: Int, height: Int, color: Int) =
        shape(OUTLINE, x, y, width, height, color)

    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) {
        image(
            IMAGE,
            imageOf(pixmap),
            0,
            0,
            pixmap.width,
            pixmap.height,
            x,
            y,
            pixmap.width,
            pixmap.height,
            1
        )
    }

    override fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int
    ) =
        drawPixmap(pixmap, x, y, srcX, srcY, srcWidth, srcHeight, srcWidth, srcHeight)

    /**
     * The `- 1` on the far edges is deliberate: a blit paints one column and one row short of what
     * it is given, at both ends, which is how the art has been drawn from the start and why a
     * background's tiles overlap by a column. A "fix" here would shift every sprite by a pixel.
     */
    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int,
    ) {
        image(
            IMAGE,
            imageOf(pixmap),
            srcX,
            srcY,
            srcWidth - 1,
            srcHeight - 1,
            x,
            y,
            dstWidth - 1,
            dstHeight - 1,
            1
        )
    }

    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int, rotationDegrees: Float,
    ) {
        val turn = turn(
            pixmap,
            x,
            y,
            srcX,
            srcY,
            srcWidth,
            srcHeight,
            dstWidth,
            dstHeight,
            rotationDegrees
        ) ?: return
        image(
            IMAGE,
            turn.image,
            0,
            0,
            turn.width,
            turn.height,
            turn.left,
            turn.top,
            turn.width,
            turn.height,
            grid
        )
    }

    /**
     * Rounded to the 256 steps a paint's alpha takes, as it always was, so a crossfade moves in the
     * same steps it did when the frame was a bitmap.
     */
    override fun drawPixmapFaded(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        alpha: Float,
    ) {
        val steps = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        if (steps <= 0) return
        if (!image(
                FADED,
                imageOf(pixmap),
                srcX,
                srcY,
                srcWidth - 1,
                srcHeight - 1,
                x,
                y,
                srcWidth - 1,
                srcHeight - 1,
                1
            )
        ) return
        if (floatCount == floats.size) floats = floats.copyOf(floats.size * 2)
        floats[floatCount++] = steps / 255f
    }

    override fun drawPixmapSilhouette(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int, color: Int,
    ) {
        if (color ushr 24 == 0) return
        if (image(
                SILHOUETTE,
                imageOf(pixmap),
                srcX,
                srcY,
                srcWidth - 1,
                srcHeight - 1,
                x,
                y,
                dstWidth - 1,
                dstHeight - 1,
                1
            )
        ) {
            ints[intCount++] = color
        }
    }

    /** The turned sprite's own turn, filled flat: the flash lands on exactly the pixels it does. */
    override fun drawPixmapSilhouette(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int, color: Int, rotationDegrees: Float,
    ) {
        if (color ushr 24 == 0) return
        val turn = turn(
            pixmap,
            x,
            y,
            srcX,
            srcY,
            srcWidth,
            srcHeight,
            dstWidth,
            dstHeight,
            rotationDegrees
        ) ?: return
        if (image(
                SILHOUETTE,
                turn.image,
                0,
                0,
                turn.width,
                turn.height,
                turn.left,
                turn.top,
                turn.width,
                turn.height,
                grid
            )
        ) {
            ints[intCount++] = color
        }
    }

    /**
     * Works the light out into a picture - unless it is the light already there - and records it to be
     * laid over everything recorded so far, and the glints over that.
     *
     * Drawn on the CPU, like a turned sprite, and for the same reason: each of its pixels is a whole
     * cell of frame pixels, so it lands on the grid when the frame is scaled up. It costs a few native
     * blits a light, and the frame takes the picture in one draw on the GPU, where lights layered up
     * there would each be another pass over the screen - and their shadows, clips the GPU is slow at.
     */
    override fun drawLighting(lighting: Lighting) {
        if (lighting !== litBy || lighting.version != litVersion) {
            renderLight(lighting)
            litBy = lighting
            litVersion = lighting.version
        }
        val image = lightImage ?: return
        reserve(LIGHT_LENGTH)
        ints[intCount++] = LIGHT
        refs += image
        addFloat(lighting.glow)

        // Over the light, each glint is its frame's mask for its light's direction, tinted the light's
        // color and added to the sprite it belongs to, laid exactly where the sprite was blitted - the
        // blit's own `- 1` and all, so a magnified sprite's glint is magnified with it.
        for (g in 0 until lighting.glintCount) {
            val glint = lighting.glint(g)
            val mask =
                glintMasks[glint.pixmap, glint.srcX, glint.srcY, glint.srcWidth, glint.srcHeight, glint.direction]
                    ?: continue
            val recorded = image(
                GLINT, mask, 0, 0, glint.srcWidth - 1, glint.srcHeight - 1,
                glint.x, glint.y, glint.dstWidth - 1, glint.dstHeight - 1, 1,
            )
            if (recorded) {
                ints[intCount++] = glint.color
                addFloat(glint.strength)
            }
        }
    }

    private fun addFloat(value: Float) {
        if (floatCount == floats.size) floats = floats.copyOf(floats.size * 2)
        floats[floatCount++] = value
    }

    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) {
        if (s != null) text(s, x, y, fontSize, col, outline = 0, outlined = false)
    }

    override fun drawOutlinedString(
        s: String,
        x: Int,
        y: Int,
        fontSize: Int,
        color: Int,
        outlineColor: Int
    ) =
        text(s, x, y, fontSize, color, outlineColor, outlined = true)

    /**
     * The width the string is laid out at, which is where its pen ends: rounded up, so text placed
     * by it never runs past the edge it was placed against.
     */
    override fun measureString(s: String, fontSize: Int): Int = layout(s, fontSize).size.width

    /**
     * Draws the recorded frame into [image], scaled to fill it, on the CPU: the frame as pixels, for
     * the recorder and the tests. At the frame's own size it is what the window shows, but for the
     * text, which the window draws at the screen's resolution.
     */
    fun drawInto(image: ImageBitmap) = drawInto(Canvas(image), image.width, image.height)

    /**
     * Draws the recorded frame into [canvas], scaled to [targetWidth] by [targetHeight], only where
     * [clip] lets it: the ambient bars read the frame's edges, and draw nothing else.
     */
    internal fun drawInto(canvas: Canvas, targetWidth: Int, targetHeight: Int, clip: Path? = null) {
        offscreen.draw(
            FRAME_DENSITY,
            LayoutDirection.Ltr,
            canvas,
            Size(targetWidth.toFloat(), targetHeight.toFloat())
        ) {
            canvas.save()
            if (clip != null) canvas.clipPath(clip)
            // Black first, for a frame that has not been recorded yet.
            shapePaint.color = Color.Black
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), shapePaint)
            canvas.scale(targetWidth / width.toFloat(), targetHeight / height.toFloat())
            this@ComposeGraphics.draw(this)
            canvas.restore()
        }
    }

    /**
     * Draws the recorded frame into [scope] in frame pixels, a frame pixel to a unit: the caller
     * scales it to its view and clips it to the frame, as [drawGameFrame] does.
     */
    internal fun draw(scope: DrawScope) {
        val canvas = scope.drawContext.canvas
        var i = 0
        var f = 0
        var r = 0
        while (i < intCount) {
            when (ints[i]) {
                RECT -> {
                    shapePaint.color = Color(ints[i + 5])
                    val left = ints[i + 1].toFloat()
                    val top = ints[i + 2].toFloat()
                    canvas.drawRect(left, top, left + ints[i + 3], top + ints[i + 4], shapePaint)
                    i += SHAPE_LENGTH
                }

                LINE -> {
                    drawLine(canvas, i)
                    i += SHAPE_LENGTH
                }

                OVAL, OUTLINE -> {
                    drawOval(canvas, i)
                    i += SHAPE_LENGTH
                }

                IMAGE -> {
                    drawImage(canvas, refs[r++] as ImageBitmap, i, imagePaint)
                    i += IMAGE_LENGTH
                }

                FADED -> {
                    fadePaint.alpha = floats[f++]
                    drawImage(canvas, refs[r++] as ImageBitmap, i, fadePaint)
                    i += IMAGE_LENGTH
                }

                SILHOUETTE -> {
                    val color = ints[i + IMAGE_LENGTH]
                    val rgb = color or OPAQUE
                    if (rgb != silhouetteRgb) {
                        silhouetteRgb = rgb
                        // Keeps the picture's alpha and throws its colors away, so what lands is
                        // its shape in the one color; the paint's alpha is how strongly.
                        silhouettePaint.colorFilter = ColorFilter.tint(Color(rgb), BlendMode.SrcIn)
                    }
                    silhouettePaint.alpha = (color ushr 24) / 255f
                    drawImage(canvas, refs[r++] as ImageBitmap, i, silhouettePaint)
                    i += IMAGE_LENGTH + 1
                }

                TEXT -> {
                    drawText(scope, refs[r++] as String, i)
                    i += TEXT_LENGTH
                }

                LIGHT -> {
                    drawLight(canvas, refs[r++] as ImageBitmap, floats[f++])
                    i += LIGHT_LENGTH
                }

                GLINT -> {
                    glintPaint.colorFilter = tint(ints[i + IMAGE_LENGTH])
                    glintPaint.alpha = floats[f++]
                    drawImage(canvas, refs[r++] as ImageBitmap, i, glintPaint)
                    i += IMAGE_LENGTH + 1
                }

                else -> error("Unknown drawing command ${ints[i]} at $i")
            }
        }
    }

    /**
     * [Raster.line]'s runs, in frame pixels whatever the grid: a line is a frame pixel wide, and
     * [Raster] lays lines down one pixel wide on whatever grid it is given.
     */
    private fun drawLine(canvas: Canvas, at: Int) {
        shapePaint.color = Color(ints[at + 5])
        Raster.line(ints[at + 1], ints[at + 2], ints[at + 3], ints[at + 4]) { left, top, w, h ->
            canvas.drawRect(
                left.toFloat(),
                top.toFloat(),
                (left + w).toFloat(),
                (top + h).toFloat(),
                shapePaint
            )
        }
    }

    /**
     * An oval, or an outline, worked out on the grid and filled as one polygon: the outline as the
     * oval and its inside traced together and filled even-odd, which leaves the ring between them.
     *
     * On the frame's own grid the inside is [Raster.ovalInterior], which leaves the oval's rim one
     * pixel thick. On a finer grid it is the oval drawn a frame pixel in from every side, which
     * keeps the rim a frame pixel thick and its steps a grid pixel fine.
     */
    private fun drawOval(canvas: Canvas, at: Int) {
        val outline = ints[at] == OUTLINE
        val scale = grid
        val x = ints[at + 1] * scale
        val y = ints[at + 2] * scale
        val w = ints[at + 3] * scale
        val h = ints[at + 4] * scale
        staircase.rewind()
        staircase.fillType = if (outline) PathFillType.EvenOdd else PathFillType.NonZero
        Raster.oval(x, y, w, h, ::traceStep)
        closeStaircase()
        if (outline) {
            if (scale == 1) {
                Raster.ovalInterior(x, y, w, h, ::traceStep)
            } else {
                Raster.oval(x + scale, y + scale, w - 2 * scale, h - 2 * scale, ::traceStep)
            }
            closeStaircase()
        }
        shapePaint.color = Color(ints[at + 5])
        if (scale != 1) {
            canvas.save()
            canvas.scale(1f / scale, 1f / scale)
        }
        canvas.drawPath(staircase, shapePaint)
        if (scale != 1) canvas.restore()
    }

    /** A blit, from its rectangles in frame pixels or, for a turned sprite, in grid pixels. */
    private fun drawImage(canvas: Canvas, image: ImageBitmap, at: Int, paint: Paint) {
        val unitsPerFramePixel = ints[at + 9]
        if (unitsPerFramePixel != 1) {
            canvas.save()
            canvas.scale(1f / unitsPerFramePixel, 1f / unitsPerFramePixel)
        }
        canvas.drawImageRect(
            image,
            IntOffset(ints[at + 1], ints[at + 2]),
            IntSize(ints[at + 3], ints[at + 4]),
            IntOffset(ints[at + 5], ints[at + 6]),
            IntSize(ints[at + 7], ints[at + 8]),
            paint,
        )
        if (unitsPerFramePixel != 1) canvas.restore()
    }

    /**
     * Laid out where it was placed and drawn from its baseline, as the game places text; the glyphs
     * themselves come out at whatever resolution [scope] is drawn at.
     */
    private fun drawText(scope: DrawScope, text: String, at: Int) {
        val layout = layout(text, ints[at + 3])
        val topLeft = Offset(ints[at + 1].toFloat(), ints[at + 2] - layout.firstBaseline)
        val color = ints[at + 4]
        val outline = ints[at + 5]
        if (ints[at + 6] != 0 && outline ushr 24 != 0) {
            scope.drawText(layout, Color(outline), topLeft, drawStyle = OUTLINE_STROKE)
        }
        // Fill said outright: on Android a layout's paint keeps the style it was last drawn with,
        // so a fill drawn with no style after the outline came out stroked, in the fill's color,
        // right over the outline.
        if (color ushr 24 != 0) scope.drawText(layout, Color(color), topLeft, drawStyle = Fill)
    }

    private fun layout(text: String, fontSize: Int): TextLayoutResult =
        textMeasurer.measure(
            text,
            styles.getOrPut(fontSize) {
                TextStyle(
                    fontSize = fontSize.sp,
                    fontFamily = FontFamily.SansSerif
                )
            },
        )

    /**
     * The light laid over the frame: everything multiplied by it, and then as much of it as [glow]
     * says added on top. Magnified nearest-neighbor, so each cell of frame pixels gets the one light
     * its pixel of the picture was worked out to.
     */
    private fun drawLight(canvas: Canvas, image: ImageBitmap, glow: Float) {
        val cells = IntSize(image.width, image.height)
        val covered = IntSize(image.width * LIGHT_CELL, image.height * LIGHT_CELL)
        canvas.drawImageRect(image, IntOffset.Zero, cells, IntOffset.Zero, covered, lightPaint)
        if (glow > 0f) {
            glowPaint.alpha = glow
            canvas.drawImageRect(image, IntOffset.Zero, cells, IntOffset.Zero, covered, glowPaint)
        }
    }

    /**
     * Works [lighting] out into [lightImage]: the dark everywhere, and every light laid over it in turn.
     *
     * The first light is laid down whole instead, the dark round its rings and all ([layFirst]), and
     * the dark filled in only around it. Nothing is under it yet but the dark, so the picture comes
     * out the same pixel for pixel, for less than half the work: a copy is the cheapest blit there is,
     * and its shadows are painted straight on in the dark's color rather than cut out of a copy of it
     * first. It is the bat's light, the largest by far and the one that throws the most shadows.
     */
    private fun renderLight(lighting: Lighting) {
        val cellsWide = (width + LIGHT_CELL - 1) / LIGHT_CELL
        val cellsHigh = (height + LIGHT_CELL - 1) / LIGHT_CELL
        val canvas = lightCanvas ?: Canvas(ImageBitmap(cellsWide, cellsHigh).also {
            lightImage = it
        }).also { lightCanvas = it }
        ambientPaint.color = Color(lighting.ambient or OPAQUE)
        val first = if (lighting.count > 0) lighting[0] else null
        if (first != null && first.intensity >= 1f && cellRadius(first) > 0) {
            layFirst(canvas, first, lighting.ambient, cellsWide, cellsHigh)
        } else {
            canvas.drawRect(0f, 0f, cellsWide.toFloat(), cellsHigh.toFloat(), ambientPaint)
            if (first != null) layLight(canvas, first)
        }
        for (i in 1 until lighting.count) layLight(canvas, lighting[i])
    }

    /** A light's radius in cells of the light picture. */
    private fun cellRadius(light: Lighting.Light): Int =
        (light.radius + LIGHT_CELL / 2) / LIGHT_CELL

    /**
     * Lays the first light down whole, onto nothing: its rings over the [ambient] dark, as one copy,
     * its shadows painted on in the dark's color, and the dark filled in round its square.
     */
    private fun layFirst(
        canvas: Canvas,
        light: Lighting.Light,
        ambient: Int,
        cellsWide: Int,
        cellsHigh: Int
    ) {
        val radius = cellRadius(light)
        val size = 2 * radius + 1
        val left = light.x.floorDiv(LIGHT_CELL) - radius
        val top = light.y.floorDiv(LIGHT_CELL) - radius
        canvas.drawImageRect(
            darkLightSprite(radius, light.color, ambient), IntOffset.Zero, IntSize(size, size),
            IntOffset(left, top), IntSize(size, size), copyPaint,
        )
        if (light.shadowCount > 0) {
            traceShadows(light, 0, 0)
            canvas.drawPath(shadowPath, ambientPaint)
        }
        fillDark(canvas, 0, 0, cellsWide, top, cellsWide, cellsHigh)
        fillDark(canvas, 0, top + size, cellsWide, cellsHigh, cellsWide, cellsHigh)
        fillDark(canvas, 0, top, left, top + size, cellsWide, cellsHigh)
        fillDark(canvas, left + size, top, cellsWide, top + size, cellsWide, cellsHigh)
    }

    /** The dark over the part of the cells from [left], [top] to [right], [bottom] that is in the picture. */
    private fun fillDark(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cellsWide: Int,
        cellsHigh: Int
    ) {
        val l = left.coerceIn(0, cellsWide)
        val t = top.coerceIn(0, cellsHigh)
        val r = right.coerceIn(0, cellsWide)
        val b = bottom.coerceIn(0, cellsHigh)
        if (r > l && b > t) canvas.drawRect(
            l.toFloat(),
            t.toFloat(),
            r.toFloat(),
            b.toFloat(),
            ambientPaint
        )
    }

    /**
     * Lays one light's rings over the light picture, at its intensity, its shadows cut out of them
     * first: each pixel taken toward the light's color by as much as the light is there.
     *
     * Laid over rather than added, because laying a picture over another is the blit Skia does fastest
     * on the CPU, where adding one costs several times as much a pixel - and the light picture is a
     * good part of the frame's pixels every tick. Light laid over light never comes out brighter than
     * the brighter of the two, so it cannot blow out; and where a colored light falls in the bat's,
     * it tints it rather than brightening it, which reads as the color of that light all the same.
     */
    private fun layLight(canvas: Canvas, light: Lighting.Light) {
        val radius = cellRadius(light)
        if (radius <= 0 || light.intensity <= 0f) return
        val size = 2 * radius + 1
        val left = light.x.floorDiv(LIGHT_CELL) - radius
        val top = light.y.floorDiv(LIGHT_CELL) - radius
        val image = lightImage ?: return
        if (left >= image.width || top >= image.height || left + size <= 0 || top + size <= 0) return
        val rings = lightSprite(radius, light.color)
        val source = if (light.shadowCount > 0) shadowed(rings, light, left, top, size) else rings
        overPaint.alpha = light.intensity
        canvas.drawImageRect(
            source,
            IntOffset.Zero,
            IntSize(size, size),
            IntOffset(left, top),
            IntSize(size, size),
            overPaint
        )
    }

    /**
     * [rings] copied out with every shadow on [light] erased from them: what of the light gets past
     * whatever stands in its way. One scratch picture serves every light, since each is laid over the
     * light picture before the next is cut, and on the CPU the draws happen as they are made.
     */
    private fun shadowed(
        rings: ImageBitmap,
        light: Lighting.Light,
        left: Int,
        top: Int,
        size: Int
    ): ImageBitmap {
        val scratch = shadowScratch?.takeIf { it.width >= size && it.height >= size }
            ?: ImageBitmap(roundUp(size), roundUp(size)).also {
                shadowScratch = it
                shadowCanvas = Canvas(it)
            }
        val canvas = shadowCanvas ?: return rings
        canvas.drawImageRect(
            rings,
            IntOffset.Zero,
            IntSize(size, size),
            IntOffset.Zero,
            IntSize(size, size),
            copyPaint
        )
        traceShadows(light, left, top)
        canvas.drawPath(shadowPath, shadowPaint)
        return scratch
    }

    /**
     * Every shadow on [light] as one path, in cells of the light picture counted from [left], [top]. The
     * shadows are all wound the same way round, so where two overlap the path fills the overlap once,
     * as the union of the two.
     */
    private fun traceShadows(light: Lighting.Light, left: Int, top: Int) {
        shadowPath.rewind()
        shadowPath.fillType = PathFillType.NonZero
        val points = light.shadowPoints
        var start = 0
        for (shadow in 0 until light.shadowCount) {
            val end = light.shadowEnd(shadow)
            shadowPath.moveTo(
                points[start] / LIGHT_CELL - left,
                points[start + 1] / LIGHT_CELL - top
            )
            var point = start + 2
            while (point < end) {
                shadowPath.lineTo(
                    points[point] / LIGHT_CELL - left,
                    points[point + 1] / LIGHT_CELL - top
                )
                point += 2
            }
            shadowPath.close()
            start = end
        }
    }

    /**
     * [lightSprite] laid over the [ambient] dark once and kept, opaque: the first light of a frame as
     * [layFirst] copies it down, the same pixels laying the light over the dark would leave.
     */
    private fun darkLightSprite(radius: Int, color: Int, ambient: Int): ImageBitmap {
        val rgb = color or OPAQUE
        val dark = ambient or OPAQUE
        for (i in darkLights.indices) {
            val lit = darkLights[i]
            if (lit.radius == radius && lit.color == rgb && lit.ambient == dark) return lit.image
        }
        val size = 2 * radius + 1
        val image = ImageBitmap(size, size)
        val canvas = Canvas(image)
        ringPaint.color = Color(dark)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), ringPaint)
        overPaint.alpha = 1f
        val rings = lightSprite(radius, rgb)
        canvas.drawImageRect(
            rings,
            IntOffset.Zero,
            IntSize(size, size),
            IntOffset.Zero,
            IntSize(size, size),
            overPaint
        )
        darkLights += DarkLight(radius, rgb, dark, image)
        return image
    }

    /** A first light, already laid over the dark; see [darkLightSprite]. */
    private class DarkLight(
        val radius: Int,
        val color: Int,
        val ambient: Int,
        val image: ImageBitmap
    )

    /**
     * A light of [radius] in [color], as a picture 2 * [radius] + 1 across: the color, as opaque at its
     * heart as the light is strong and clear at its edge, in the rings [Lighting.rings] gives - each
     * laid down as [Raster] fills an oval, so that it is the same pixels on every platform. Drawn the
     * first time a light of that radius and color is, and kept: a run's lights come in a handful of
     * radii and colors, and a light tinted as it is drawn would cost every pixel of it a filter, every
     * frame.
     */
    private fun lightSprite(radius: Int, color: Int): ImageBitmap {
        if (radius >= lightSprites.size) lightSprites =
            lightSprites.copyOf(maxOf(radius + 1, lightSprites.size * 2))
        val tinted =
            lightSprites[radius] ?: ArrayList<TintedLight>(2).also { lightSprites[radius] = it }
        val rgb = color or OPAQUE
        for (i in tinted.indices) {
            if (tinted[i].color == rgb) return tinted[i].image
        }

        val size = 2 * radius + 1
        val image = ImageBitmap(size, size)
        val canvas = Canvas(image)
        val rings = Lighting.rings(radius)
        for (i in rings.indices step 2) {
            val ring = rings[i]
            ringPaint.color = Color(rgb).copy(alpha = rings[i + 1] / 255f)
            Raster.oval(
                radius - ring,
                radius - ring,
                2 * ring + 1,
                2 * ring + 1
            ) { left, top, w, h ->
                canvas.drawRect(
                    left.toFloat(),
                    top.toFloat(),
                    (left + w).toFloat(),
                    (top + h).toFloat(),
                    ringPaint
                )
            }
        }
        tinted += TintedLight(rgb, image)
        return image
    }

    /** A light's rings in one color; see [lightSprite]. */
    private class TintedLight(val color: Int, val image: ImageBitmap)

    /**
     * The pixels of a frame of [surface] that glint in a light from [direction], as a picture of the
     * frame's size: white, and as opaque as each pixel's glint is bright. Null for a frame that catches
     * none of it. Laid down a run of a row at a time, the way [Raster] shapes are, so it is the same
     * pixels on every platform.
     */
    private fun glintMask(surface: Gloss.Surface, direction: Int): ImageBitmap? {
        val levels = surface.glints(direction)
        if (levels.all { it.toInt() == 0 }) return null
        val width = surface.width
        val image = ImageBitmap(width, surface.height)
        val canvas = Canvas(image)
        for (y in 0 until surface.height) {
            var x = 0
            while (x < width) {
                val level = levels[y * width + x].toInt()
                var end = x + 1
                while (end < width && levels[y * width + end].toInt() == level) end++
                if (level > 0) {
                    maskPaint.color = Color.White.copy(alpha = level / Gloss.LEVELS.toFloat())
                    canvas.drawRect(x.toFloat(), y.toFloat(), end.toFloat(), y + 1f, maskPaint)
                }
                x = end
            }
        }
        return image
    }

    /**
     * The filter that tints a glint's white mask its light's [color] as it is drawn on the GPU, where a
     * filter costs next to nothing: kept for each color asked for, since a run's lights come in a
     * handful of colors and a filter is an allocation. Past [MAX_TINTS] colors the oldest is let go.
     */
    private fun tint(color: Int): ColorFilter {
        val rgb = color or OPAQUE
        for (i in 0 until tintCount) {
            if (tintColors[i] == rgb) return tintFilters[i]!!
        }
        val filter = ColorFilter.tint(Color(rgb), BlendMode.Modulate)
        val slot = if (tintCount < MAX_TINTS) tintCount++ else (nextTint++ % MAX_TINTS)
        tintColors[slot] = rgb
        tintFilters[slot] = filter
        return filter
    }

    private fun shape(op: Int, a: Int, b: Int, c: Int, d: Int, color: Int) {
        // A clear color changes nothing, and the outer rings of a faint halo come out clear.
        if (color ushr 24 == 0) return
        reserve(SHAPE_LENGTH)
        ints[intCount++] = op
        ints[intCount++] = a
        ints[intCount++] = b
        ints[intCount++] = c
        ints[intCount++] = d
        ints[intCount++] = color
    }

    /**
     * Records a blit of [image], already in the exact rectangles it is drawn from and to, the
     * destination in [unitsPerFramePixel] units to a frame pixel; false, and nothing recorded, when
     * one of the rectangles is empty.
     */
    private fun image(
        op: Int, image: ImageBitmap,
        srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstX: Int, dstY: Int, dstWidth: Int, dstHeight: Int,
        unitsPerFramePixel: Int,
    ): Boolean {
        if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) return false
        // One more than a blit needs, for the color a silhouette's caller adds.
        reserve(IMAGE_LENGTH + 1)
        ints[intCount++] = op
        ints[intCount++] = srcX
        ints[intCount++] = srcY
        ints[intCount++] = srcWidth
        ints[intCount++] = srcHeight
        ints[intCount++] = dstX
        ints[intCount++] = dstY
        ints[intCount++] = dstWidth
        ints[intCount++] = dstHeight
        ints[intCount++] = unitsPerFramePixel
        refs += image
        return true
    }

    private fun text(
        s: String,
        x: Int,
        y: Int,
        fontSize: Int,
        color: Int,
        outline: Int,
        outlined: Boolean
    ) {
        reserve(TEXT_LENGTH)
        ints[intCount++] = TEXT
        ints[intCount++] = x
        ints[intCount++] = y
        ints[intCount++] = fontSize
        ints[intCount++] = color
        ints[intCount++] = outline
        ints[intCount++] = if (outlined) 1 else 0
        refs += s
    }

    private fun reserve(count: Int) {
        if (intCount + count > ints.size) ints = ints.copyOf(maxOf(ints.size * 2, intCount + count))
    }

    private fun imageOf(pixmap: Pixmap): ImageBitmap = (pixmap as ImagePixmap).image

    /**
     * The sprite turned on the grid, into a picture of its own: what a turned blit used to lay on
     * the framebuffer, every pixel of it a whole grid pixel, ready to be drawn upright like any
     * other. Null when there is nothing to draw.
     *
     * Turned the way it always was - about the center of its box, which does not move or grow, with
     * the blit's `- 1` edges - by the same Canvas calls, only into the picture rather than the frame,
     * nearest-neighbor and on the CPU. The picture is the box the turned sprite covers, in whole grid
     * pixels, and is drawn back at that box's place, so the sprite lands where it always did.
     *
     * A sprite's flash is drawn straight after the sprite with the same turn, and gets the sprite's
     * picture rather than turning it again.
     */
    private fun turn(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int, degrees: Float,
    ): Turn? {
        if (srcWidth <= 1 || srcHeight <= 1 || dstWidth <= 1 || dstHeight <= 1) return null
        if (turnsUsed > 0) {
            val last = turns[turnsUsed - 1]
            if (last.holds(
                    pixmap,
                    x,
                    y,
                    srcX,
                    srcY,
                    srcWidth,
                    srcHeight,
                    dstWidth,
                    dstHeight,
                    degrees,
                    grid
                )
            ) return last
        }

        // The box the blit fills, its `- 1` included, and its pivot, all in grid pixels.
        val scale = grid
        val boxLeft = x * scale
        val boxTop = y * scale
        val boxWidth = (dstWidth - 1) * scale
        val boxHeight = (dstHeight - 1) * scale
        val pivotX = (x + dstWidth / 2f) * scale
        val pivotY = (y + dstHeight / 2f) * scale

        // Its corners turned about the pivot the way Canvas.rotate turns them: clockwise, with y
        // pointing down.
        val radians = degrees * RADIANS_PER_DEGREE
        val cos = cos(radians)
        val sin = sin(radians)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (corner in 0 until 4) {
            val dx = (if (corner and 1 == 0) boxLeft else boxLeft + boxWidth) - pivotX
            val dy = (if (corner and 2 == 0) boxTop else boxTop + boxHeight) - pivotY
            val turnedX = pivotX + dx * cos - dy * sin
            val turnedY = pivotY + dx * sin + dy * cos
            minX = minOf(minX, turnedX)
            minY = minOf(minY, turnedY)
            maxX = maxOf(maxX, turnedX)
            maxY = maxOf(maxY, turnedY)
        }
        val left = floor(minX).toInt()
        val top = floor(minY).toInt()
        val width = ceil(maxX).toInt() - left
        val height = ceil(maxY).toInt() - top
        if (width <= 0 || height <= 0) return null

        val turn = slot(width, height)
        val canvas = turn.canvas
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), erasePaint)
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.translate(-left.toFloat(), -top.toFloat())
        canvas.translate(pivotX, pivotY)
        canvas.rotate(degrees)
        canvas.translate(-pivotX, -pivotY)
        canvas.drawImageRect(
            imageOf(pixmap),
            IntOffset(srcX, srcY),
            IntSize(srcWidth - 1, srcHeight - 1),
            IntOffset(boxLeft, boxTop),
            IntSize(boxWidth, boxHeight),
            imagePaint,
        )
        canvas.restore()
        turn.hold(
            pixmap,
            x,
            y,
            srcX,
            srcY,
            srcWidth,
            srcHeight,
            dstWidth,
            dstHeight,
            degrees,
            scale
        )
        turn.left = left
        turn.top = top
        turn.width = width
        turn.height = height
        return turn
    }

    /**
     * The next of this frame's turn pictures, at least [width] by [height]. Each frame reuses the
     * last one's pictures in order, so a sprite that turns every frame does not allocate one every
     * frame; a picture only grows, rounded up, so one that turns a little wider next frame still fits.
     */
    private fun slot(width: Int, height: Int): Turn {
        val existing = turns.getOrNull(turnsUsed)
        val turn =
            if (existing != null && existing.image.width >= width && existing.image.height >= height) {
                existing
            } else {
                val image = ImageBitmap(
                    roundUp(maxOf(width, existing?.image?.width ?: 0)),
                    roundUp(maxOf(height, existing?.image?.height ?: 0)),
                )
                Turn(
                    image,
                    Canvas(image)
                ).also { if (existing == null) turns += it else turns[turnsUsed] = it }
            }
        turnsUsed++
        return turn
    }

    /** One run of an oval as a step of a staircase: its left-hand side traced down, its right-hand end kept. */
    private fun traceStep(left: Int, top: Int, width: Int, height: Int) {
        val x = left.toFloat()
        if (steps == 0) staircase.moveTo(x, top.toFloat()) else staircase.lineTo(x, top.toFloat())
        staircase.lineTo(x, (top + height).toFloat())
        if (steps * 3 == stepEnds.size) stepEnds = stepEnds.copyOf(stepEnds.size * 2)
        stepEnds[steps * 3] = left + width
        stepEnds[steps * 3 + 1] = top
        stepEnds[steps * 3 + 2] = top + height
        steps++
    }

    /**
     * Back up the right-hand ends of the runs traced down so far, closing the polygon.
     *
     * An oval's runs come as one polygon rather than as rectangles because every draw call has a
     * cost of its own, and the sixteen rings of a halo are a lot of rectangles. Every corner of the
     * polygon is a pixel corner, so it covers exactly the rectangles' pixels at any scale.
     */
    private fun closeStaircase() {
        for (i in steps - 1 downTo 0) {
            val right = stepEnds[i * 3].toFloat()
            staircase.lineTo(right, stepEnds[i * 3 + 2].toFloat())
            staircase.lineTo(right, stepEnds[i * 3 + 1].toFloat())
        }
        if (steps > 0) staircase.close()
        steps = 0
    }

    /**
     * A picture a sprite is turned into, where its box lands in grid pixels, and which sprite at
     * which turn on which grid it holds this frame.
     */
    private class Turn(val image: ImageBitmap, val canvas: Canvas) {
        var left = 0
        var top = 0
        var width = 0
        var height = 0
        private var pixmap: Pixmap? = null
        private val box = IntArray(9)
        private var degrees = 0f

        fun hold(
            pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
            dstWidth: Int, dstHeight: Int, degrees: Float, grid: Int,
        ) {
            this.pixmap = pixmap
            this.degrees = degrees
            box[0] = x; box[1] = y; box[2] = srcX; box[3] = srcY; box[4] = srcWidth
            box[5] = srcHeight; box[6] = dstWidth; box[7] = dstHeight; box[8] = grid
        }

        fun holds(
            pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
            dstWidth: Int, dstHeight: Int, degrees: Float, grid: Int,
        ): Boolean = this.pixmap === pixmap && this.degrees == degrees &&
                box[0] == x && box[1] == y && box[2] == srcX && box[3] == srcY && box[4] == srcWidth &&
                box[5] == srcHeight && box[6] == dstWidth && box[7] == dstHeight && box[8] == grid
    }

    companion object {
        /** The draw phase, as a trace names it: the recorded frame drawn into the window. */
        const val DRAW_SECTION = "Frame.draw"

        private const val RECT = 1
        private const val LINE = 2
        private const val OVAL = 3
        private const val OUTLINE = 4
        private const val IMAGE = 5
        private const val FADED = 6
        private const val SILHOUETTE = 7
        private const val TEXT = 8
        private const val LIGHT = 9

        /**
         * Frame pixels to a pixel of the light picture, each way. The light is worked out at half the
         * frame's resolution: a quarter of the pixels to fill, to light and to hand the GPU every tick,
         * which is most of what the light costs. Light falls off smoothly and is read for where it
         * falls, so its cells go unnoticed in the pools, and a shadow's edge steps two pixels at a time
         * instead of one.
         */
        private const val LIGHT_CELL = 2
        private const val GLINT = 10

        /** How many ints each kind of command takes, its opcode included. */
        private const val SHAPE_LENGTH = 6
        private const val IMAGE_LENGTH = 10
        private const val TEXT_LENGTH = 7
        private const val LIGHT_LENGTH = 1

        /** How many light colors [tint] keeps a filter for. */
        private const val MAX_TINTS = 32

        private const val OPAQUE = 0xFF000000.toInt()
        private const val RADIANS_PER_DEGREE = 0.017453292f
        private const val TEXT_CACHE_SIZE = 64

        private val FRAME_DENSITY = Density(1f)

        /**
         * An outline a frame pixel thick: the stroke is centered on the glyphs' edges, and the fill
         * drawn over it covers its inner half. Rounded at the corners, which a mitered stroke would
         * spike out of.
         */
        private val OUTLINE_STROKE = Stroke(width = 2f, join = StrokeJoin.Round)

        private fun roundUp(size: Int) = (size + 15) and 15.inv()
    }
}
