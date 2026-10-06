package at.smiech.engine.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
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
import at.smiech.engine.Dither
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
 * The [Graphics] every platform draws the game with, through Compose's Canvas API: HWUI, and so the
 * GPU, on Android; Skia on the desktop and on iOS.
 *
 * A screen's present is recorded here call by call, and the host draws the recording into its
 * Compose Canvas with [drawGameFrame]. Recorded rather than drawn immediately because the game loop
 * runs in Compose's frame callback while drawing happens later, in the draw phase, and because a
 * frame is drawn more than once: into the window, into the small picture [AmbientBars] reads the
 * edges from, and into a full-size one for the recorder and the tests ([drawInto]).
 *
 * Two grids are at work, by default the same one:
 * - The frame, [width] by [height], is what the game is laid out and played in. Every call arrives
 *   in its pixels, and the host scales it to the screen as a whole.
 * - The pixel grid is what the pixel art is drawn on: [gridScale] grid pixels to a frame pixel each
 *   way. At the default of 1 it is the frame itself.
 *
 * Everything but text lands on the grid at any screen size, because it is drawn aliased and
 * nearest-neighbor under one scale. Sprites and rectangles are whole frame pixels, so they magnify
 * into blocks. Ovals are computed on the grid ([Raster]), and a turned sprite is turned on it
 * before it is drawn ([turn]); turned at the screen's resolution, its pixels would come out as
 * squares tilted against the grid and finer than it.
 *
 * Text is the deliberate exception: laid out in frame pixels but drawn antialiased
 * at the screen's resolution, so the HUD reads crisply over the pixel art. An
 * outline is a stroke around the glyphs.
 *
 * @param loadImage decodes an asset; the host knows where its assets are and how to decode them
 *   (see `DesktopGame` for the desktop's extra step).
 * @param fontFamilyResolver how the platform finds a font.
 */
class ComposeGraphics(
    override val width: Int,
    override val height: Int,
    private val loadImage: (filename: String) -> ImageBitmap,
    fontFamilyResolver: FontFamily.Resolver,
) : Graphics {

    /**
     * Grid pixels per frame pixel, each way; the default of 1 draws on the frame's own pixels.
     * Takes effect at the start of the next frame, so a frame is never drawn on two grids.
     *
     * Whole numbers only, so sprite pixels and rectangle edges, which fall on frame pixels, fall on
     * grid pixels too.
     *
     * A finer grid changes only what is computed on it: an oval keeps its box and an outline its
     * one-frame-pixel weight, with finer steps round the curve; a turned sprite keeps its pixel
     * size, with finer steps along its edges. Lines stay on the frame's grid, since their weight is
     * a frame pixel and [Raster] only draws lines one grid pixel wide.
     */
    var gridScale: Int = 1
        set(value) {
            require(value >= 1) { "A grid scale is a whole number of grid pixels to a frame pixel, at least 1: $value" }
            field = value
        }

    /** The grid the frame being recorded is drawn on: [gridScale] as of its start. */
    private var grid = 1

    /**
     * Lays text out at density 1, which makes a font size in sp a size in frame pixels. The cache
     * is larger than the default eight because the HUD alone lays out more strings than that per
     * frame, each measured where it is placed and again where it is drawn.
     */
    private val textMeasurer =
        TextMeasurer(fontFamilyResolver, FRAME_DENSITY, LayoutDirection.Ltr, TEXT_CACHE_SIZE)
    private val styles = HashMap<Int, TextStyle>()

    /**
     * The recorded frame: each command's opcode and arguments in [ints], its float arguments in
     * [floats], and the picture or string it draws in [refs]. Rewound rather than reallocated each
     * frame, so recording allocates nothing.
     */
    private var ints = IntArray(1024)
    private var intCount = 0
    private var floats = FloatArray(64)
    private var floatCount = 0
    private val refs = ArrayList<Any>(256)

    /** The turned sprites, one per turned blit in the frame; see [turn]. */
    private val turns = ArrayList<Turn>()
    private var turnsUsed = 0

    // Every paint is aliased: an antialiased edge, scaled up, would blur the grid. Compose's
    // Paint() is antialiased by default.
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
     * The path an oval is traced into as one polygon, and the right-hand ends of the runs traced so
     * far (an x, a top and a bottom each). Reused across shapes, so drawing one allocates nothing.
     */
    private val staircase = Path()
    private var stepEnds = IntArray(3 * 128)
    private var steps = 0

    private val offscreen = CanvasDrawScope()

    /**
     * The frame's light as [drawLighting] last rendered it, one pixel per [LIGHT_CELL]-square cell
     * of frame pixels, drawn on the CPU; and the [Lighting] and [Lighting.version] it shows, so
     * unchanged light is not rendered again.
     */
    private var lightImage: ImageBitmap? = null
    private var lightCanvas: Canvas? = null
    private var litBy: Lighting? = null
    private var litVersion = 0

    /** Every light requested so far, by radius and then color, rendered once; see [lightSprite]. */
    private var lightSprites = arrayOfNulls<ArrayList<TintedLight>>(64)

    /** Frames' first lights, each pre-composited over the dark; see [darkLightSprite]. */
    private val darkLights = ArrayList<DarkLight>(2)

    /** Where a shadow-casting light has its shadows cut out; see [shadowed]. */
    private var shadowScratch: ImageBitmap? = null
    private var shadowCanvas: Canvas? = null
    private val shadowPath = Path()

    // The light is drawn in whole cells of frame pixels, so aliased edges land on the frame's grid:
    // a shadow's edge falls between cells, never across one. Every draw into it is a plain copy
    // (Src) or SrcOver, the two blits Skia does fastest on the CPU; adding light, or tinting it
    // while drawing, costs several times as much per pixel.
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
     * How each frame of the art glints for each light direction: a picture the frame's size, white
     * where it catches the light and as opaque as the glint is bright, or null where it catches
     * none. Computed from the frame's pixels on first use; see [Gloss].
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

    /**
     * Each frame of the art at each level of [Dither] it has been drawn at: the frame's own
     * picture with the pixels that level leaves out cleared. Made on first use and kept, like the
     * glints, so a sprite pulsing through the levels costs a plain blit a frame.
     */
    private val ditheredFrames =
        FrameCache(Dither.LEVELS) { pixmap, x, y, width, height, level ->
            dither(pixmap, x, y, width, height, level)
        }

    /** Each level's pattern as a tile repeated across whatever it is drawn over; see [ditherShader]. */
    private val ditherShaders = arrayOfNulls<Shader>(Dither.LEVELS)
    private val ditherPaint = Paint().apply {
        isAntiAlias = false; blendMode = BlendMode.DstIn; filterQuality = FilterQuality.None
    }

    /** The filter tinting glints in each light color seen so far; see [tint]. */
    private val tintColors = IntArray(MAX_TINTS)
    private val tintFilters = arrayOfNulls<ColorFilter>(MAX_TINTS)
    private var tintCount = 0

    /** The slot the next new tint replaces once all are in use: the oldest. */
    private var nextTint = 0

    override fun newPixmap(filename: String): Pixmap =
        ImagePixmap(loadImage(filename))

    /**
     * Starts the recording over, since nothing recorded before a clear could show through it. Every
     * screen clears first, which keeps the recording one frame long and starts the frame on the
     * grid [gridScale] asks for.
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
     * it is given, which is how the art has always been drawn and why background tiles overlap by a
     * column. A "fix" here would shift every sprite by a pixel.
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
     * The alpha is rounded to a paint's 256 steps, so a crossfade moves in the same
     * steps everywhere.
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
        addFloat(steps / 255f)
    }

    /**
     * The frame's dithered picture (see [ditheredFrames]), blitted as the frame itself would be,
     * `- 1` included. A level that keeps every pixel is the plain blit, and one that keeps none
     * draws nothing.
     */
    override fun drawPixmapDithered(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int, coverage: Float,
    ) {
        val level = Dither.level(coverage)
        if (level <= 0) return
        if (level >= Dither.LEVELS) {
            return drawPixmap(pixmap, x, y, srcX, srcY, srcWidth, srcHeight, dstWidth, dstHeight)
        }
        val dithered = ditheredFrames[pixmap, srcX, srcY, srcWidth, srcHeight, level] ?: return
        image(
            IMAGE,
            dithered,
            0,
            0,
            srcWidth - 1,
            srcHeight - 1,
            x,
            y,
            dstWidth - 1,
            dstHeight - 1,
            1
        )
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

    /** The turned sprite's own picture, filled flat, so the flash covers exactly its pixels. */
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
     * Renders the light into a picture, unless it is unchanged, and records it to be laid over
     * everything recorded so far, with the glints over that.
     *
     * Rendered on the CPU, like a turned sprite, so each of its pixels is a whole cell of frame
     * pixels and lands on the grid when the frame is scaled up. It costs a few native blits per
     * light, and the frame takes the result in one GPU draw; layered on the GPU, each light would
     * be another pass over the screen, and its shadows clips, which the GPU is slow at.
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

        // Over the light, each glint is its frame's mask for the light's direction, tinted the
        // light's color and added exactly where the sprite was blitted, `- 1` included, so a
        // magnified sprite's glint is magnified with it.
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

    /** The laid-out width, rounded up, so text placed by it never runs past its alignment edge. */
    override fun measureString(s: String, fontSize: Int): Int = layout(s, fontSize).size.width

    /**
     * Draws the recorded frame into [image], scaled to fill it, on the CPU, for the recorder and
     * the tests. At the frame's own size it matches the window except for text, which the window
     * draws at the screen's resolution.
     */
    fun drawInto(image: ImageBitmap) = drawInto(Canvas(image), image.width, image.height)

    /**
     * Draws the recorded frame into [canvas], scaled to [targetWidth] by [targetHeight] and limited
     * to [clip], which the ambient bars use to draw only the frame's edges.
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
     * Draws the recorded frame into [scope], one unit per frame pixel; the caller scales it to its
     * view and clips it to the frame, as [drawGameFrame] does.
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
                        // Keeps the picture's alpha and replaces its colors, leaving its shape in
                        // one color; the paint's alpha sets the strength.
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
     * [Raster.line]'s runs, in frame pixels on any grid: a line is a frame pixel wide, and [Raster]
     * draws lines one pixel wide on whatever grid it is given.
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
     * An oval or outline, computed on the grid and filled as one polygon. An outline is the oval
     * and its inside traced together and filled even-odd, leaving the ring between them.
     *
     * On the frame's own grid the inside is [Raster.ovalInterior], leaving a one-pixel rim. On a
     * finer grid it is the oval inset a frame pixel on every side, keeping the rim a frame pixel
     * thick with grid-pixel steps.
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
     * Draws text from its baseline, as the game places it; the glyphs come out at whatever
     * resolution [scope] is drawn at.
     */
    private fun drawText(scope: DrawScope, text: String, at: Int) {
        val layout = layout(text, ints[at + 3])
        val topLeft = Offset(ints[at + 1].toFloat(), ints[at + 2] - layout.firstBaseline)
        val color = ints[at + 4]
        val outline = ints[at + 5]
        if (ints[at + 6] != 0 && outline ushr 24 != 0) {
            scope.drawText(layout, Color(outline), topLeft, drawStyle = OUTLINE_STROKE)
        }
        // Fill is given explicitly: on Android a layout's paint keeps the style it was last drawn
        // with, so a fill without a style after the outline came out stroked over the outline.
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
     * The light laid over the frame: everything multiplied by it, then [glow] of it added on top.
     * Magnified nearest-neighbor, so each cell of frame pixels gets its pixel of the light picture.
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
     * Renders [lighting] into [lightImage]: the dark everywhere, with every light
     * laid over it in turn.
     *
     * The first light is instead copied down whole, dark corners included ([layFirst]), with the
     * dark filled in only around it. Nothing is under it but the dark, so the result is identical
     * pixel for pixel at less than half the cost: a copy is the cheapest blit, and its shadows are
     * painted on in the dark's color rather than cut from a copy first. It is the bat's light, by
     * far the largest and the one casting the most shadows.
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
     * Lays the first light onto an empty picture: its rings over the [ambient] dark as one copy,
     * its shadows painted on in the dark's color, and the dark filled in around its square.
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

    /**
     * Fills the cells from [left], [top] to [right], [bottom] with the dark,
     * clipped to the picture.
     */
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
     * Lays one light's rings, shadows cut out first, over the light picture at its intensity: each
     * pixel moves toward the light's color by as much light as reaches it.
     *
     * Laid over (SrcOver) rather than added, because that is the blit Skia does fastest on the CPU,
     * where adding costs several times as much per pixel, and the light picture is a large share of
     * the frame every tick. Light over light is never brighter than the brighter of the two, so it
     * cannot blow out; a colored light inside the bat's tints it rather than brightening it, which
     * still reads as that light's color.
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
     * [rings] copied with every shadow on [light] erased: the part of the light that gets past what
     * stands in its way. One scratch picture serves every light, since each is laid over the light
     * picture before the next is cut, and CPU draws execute immediately.
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
     * Every shadow on [light] as one path, in cells of the light picture relative to [left], [top].
     * The shadows all wind the same way, so overlaps fill once, as their union.
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
     * [lightSprite] composited over the [ambient] dark once and kept, opaque: a frame's first light
     * as [layFirst] copies it down, with the same pixels laying it over the dark would produce.
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
     * A light of [radius] in [color], as a picture 2 * [radius] + 1 across: the color, opaque at
     * the center and clear at the edge, in the rings [Lighting.rings] gives, each filled as
     * [Raster] fills an oval so the pixels match on every platform. Rendered on first use and kept:
     * a run's lights come in a handful of radii and colors, and tinting while drawing would cost a
     * filter on every pixel, every frame.
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
     * The pixels of [surface] that glint in a light from [direction], as a picture of the frame's
     * size: white, as opaque as each glint is bright; null if none glint. Filled run by run like
     * [Raster] shapes, so the pixels match on every platform.
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
     * The [width] by [height] frame at [x], [y] on [pixmap], with every pixel [Dither] leaves out
     * at [level] cleared: copied down whole, then kept only where the level's pattern, tiled from
     * the frame's corner, is opaque. On the CPU and once, like a glint's mask.
     */
    private fun dither(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        level: Int
    ): ImageBitmap {
        val image = ImageBitmap(width, height)
        val canvas = Canvas(image)
        canvas.drawImageRect(
            imageOf(pixmap),
            IntOffset(x, y),
            IntSize(width, height),
            IntOffset.Zero,
            IntSize(width, height),
            copyPaint
        )
        ditherPaint.shader = ditherShader(level)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), ditherPaint)
        return image
    }

    /**
     * [level]'s pattern as a [Dither.TILE]-square tile, opaque where it keeps a pixel and clear
     * where it leaves one out, repeated in both directions. Made once a level.
     */
    private fun ditherShader(level: Int): Shader = ditherShaders[level] ?: run {
        val tile = ImageBitmap(Dither.TILE, Dither.TILE)
        val canvas = Canvas(tile)
        maskPaint.color = Color.White
        for (row in 0 until Dither.TILE) for (column in 0 until Dither.TILE) {
            if (!Dither.keeps(column, row, level)) continue
            canvas.drawRect(
                column.toFloat(),
                row.toFloat(),
                column + 1f,
                row + 1f,
                maskPaint
            )
        }
        ImageShader(tile, TileMode.Repeated, TileMode.Repeated).also { ditherShaders[level] = it }
    }

    /**
     * The filter that tints a glint's white mask in its light's [color] as it is drawn on the GPU,
     * where a filter is nearly free. Cached per color, since a run's lights come in a handful of
     * colors and each filter is an allocation; past [MAX_TINTS] colors the oldest is replaced.
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

    /** Records a shape command. */
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
     * Records a blit of [image] between the exact rectangles given, the destination in
     * [unitsPerFramePixel] units per frame pixel. Returns false, recording nothing, when either
     * rectangle is empty.
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

    /** Records a text command. */
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
     * The sprite turned on the grid into a picture of its own, every pixel a whole grid pixel,
     * ready to be drawn upright like any other. Null when there is nothing to draw.
     *
     * Turned about the center of its box, which does not move or grow, with the blit's `- 1` edges,
     * nearest-neighbor on the CPU. The picture covers the turned sprite's bounding box in whole
     * grid pixels and is drawn back at that box's position.
     *
     * A sprite's flash is drawn right after the sprite with the same turn, and reuses the sprite's
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

        // Its corners turned about the pivot as Canvas.rotate turns them:
        // clockwise, y pointing down.
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
     * previous frame's pictures in order, so a sprite turning every frame does not allocate;
     * pictures only grow, rounded up, so a slightly wider turn next frame still fits.
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
     * Traces back up the right-hand ends of the runs so far, closing the polygon.
     *
     * An oval is one polygon rather than many rectangles because every draw call has a cost, and a
     * halo's sixteen rings are a lot of rectangles. Every corner is a pixel corner, so the polygon
     * covers exactly the rectangles' pixels at any scale.
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
     * A picture a sprite is turned into, where its box lands in grid pixels, and which sprite,
     * angle and grid it holds this frame.
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
        /** The trace section for the draw phase: the recorded frame drawn into the window. */
        const val DRAW_SECTION = "Frame.draw"

        // Recorded command opcodes.
        private const val RECT = 1
        private const val LINE = 2
        private const val OVAL = 3
        private const val OUTLINE = 4
        private const val IMAGE = 5
        private const val FADED = 6
        private const val SILHOUETTE = 7
        private const val TEXT = 8
        private const val LIGHT = 9
        private const val GLINT = 10

        /**
         * Frame pixels per pixel of the light picture, each way. Half resolution
         * means a quarter of the pixels to fill, light and upload every tick, which
         * is most of the lighting's cost. Light falls off smoothly, so the cells go
         * unnoticed; a shadow's edge steps two pixels at a time.
         */
        private const val LIGHT_CELL = 2

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
         * An outline a frame pixel thick: the stroke is centered on the glyphs' edges and the fill
         * covers its inner half. Round joins, because mitered ones spike at sharp corners.
         */
        private val OUTLINE_STROKE = Stroke(width = 2f, join = StrokeJoin.Round)

        private fun roundUp(size: Int) = (size + 15) and 15.inv()
    }
}
