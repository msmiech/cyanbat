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
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.Raster
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
    private val textMeasurer = TextMeasurer(fontFamilyResolver, FRAME_DENSITY, LayoutDirection.Ltr, TEXT_CACHE_SIZE)
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
    private val imagePaint = Paint().apply { isAntiAlias = false; filterQuality = FilterQuality.None }
    private val fadePaint = Paint().apply { isAntiAlias = false; filterQuality = FilterQuality.None }
    private val silhouettePaint = Paint().apply { isAntiAlias = false; filterQuality = FilterQuality.None }
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

    override fun newPixmap(filename: String, format: Graphics.PixmapFormat): Pixmap = ImagePixmap(loadImage(filename))

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
        image(IMAGE, imageOf(pixmap), 0, 0, pixmap.width, pixmap.height, x, y, pixmap.width, pixmap.height, 1)
    }

    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int) =
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
        image(IMAGE, imageOf(pixmap), srcX, srcY, srcWidth - 1, srcHeight - 1, x, y, dstWidth - 1, dstHeight - 1, 1)
    }

    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int, rotationDegrees: Float,
    ) {
        val turn = turn(pixmap, x, y, srcX, srcY, srcWidth, srcHeight, dstWidth, dstHeight, rotationDegrees) ?: return
        image(IMAGE, turn.image, 0, 0, turn.width, turn.height, turn.left, turn.top, turn.width, turn.height, grid)
    }

    /**
     * Rounded to the 256 steps a paint's alpha takes, as it always was, so a crossfade moves in the
     * same steps it did when the frame was a bitmap.
     */
    override fun drawPixmapFaded(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int, alpha: Float,
    ) {
        val steps = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        if (steps <= 0) return
        if (!image(FADED, imageOf(pixmap), srcX, srcY, srcWidth - 1, srcHeight - 1, x, y, srcWidth - 1, srcHeight - 1, 1)) return
        if (floatCount == floats.size) floats = floats.copyOf(floats.size * 2)
        floats[floatCount++] = steps / 255f
    }

    override fun drawPixmapSilhouette(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int, color: Int,
    ) {
        if (color ushr 24 == 0) return
        if (image(SILHOUETTE, imageOf(pixmap), srcX, srcY, srcWidth - 1, srcHeight - 1, x, y, dstWidth - 1, dstHeight - 1, 1)) {
            ints[intCount++] = color
        }
    }

    /** The turned sprite's own turn, filled flat: the flash lands on exactly the pixels it does. */
    override fun drawPixmapSilhouette(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int, color: Int, rotationDegrees: Float,
    ) {
        if (color ushr 24 == 0) return
        val turn = turn(pixmap, x, y, srcX, srcY, srcWidth, srcHeight, dstWidth, dstHeight, rotationDegrees) ?: return
        if (image(SILHOUETTE, turn.image, 0, 0, turn.width, turn.height, turn.left, turn.top, turn.width, turn.height, grid)) {
            ints[intCount++] = color
        }
    }

    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) {
        if (s != null) text(s, x, y, fontSize, col, outline = 0, outlined = false)
    }

    override fun drawOutlinedString(s: String, x: Int, y: Int, fontSize: Int, color: Int, outlineColor: Int) =
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
        offscreen.draw(FRAME_DENSITY, LayoutDirection.Ltr, canvas, Size(targetWidth.toFloat(), targetHeight.toFloat())) {
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
            canvas.drawRect(left.toFloat(), top.toFloat(), (left + w).toFloat(), (top + h).toFloat(), shapePaint)
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
            styles.getOrPut(fontSize) { TextStyle(fontSize = fontSize.sp, fontFamily = FontFamily.SansSerif) },
        )

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

    private fun text(s: String, x: Int, y: Int, fontSize: Int, color: Int, outline: Int, outlined: Boolean) {
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
            if (last.holds(pixmap, x, y, srcX, srcY, srcWidth, srcHeight, dstWidth, dstHeight, degrees, grid)) return last
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
        turn.hold(pixmap, x, y, srcX, srcY, srcWidth, srcHeight, dstWidth, dstHeight, degrees, scale)
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
        val turn = if (existing != null && existing.image.width >= width && existing.image.height >= height) {
            existing
        } else {
            val image = ImageBitmap(
                roundUp(maxOf(width, existing?.image?.width ?: 0)),
                roundUp(maxOf(height, existing?.image?.height ?: 0)),
            )
            Turn(image, Canvas(image)).also { if (existing == null) turns += it else turns[turnsUsed] = it }
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

        /** How many ints each kind of command takes, its opcode included. */
        private const val SHAPE_LENGTH = 6
        private const val IMAGE_LENGTH = 10
        private const val TEXT_LENGTH = 7

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
