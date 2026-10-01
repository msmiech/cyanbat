package at.smiech.engine.impl

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.graphics.withRotation
import at.smiech.engine.Graphics
import at.smiech.engine.Graphics.PixmapFormat
import at.smiech.engine.Pixmap
import at.smiech.engine.Raster
import java.io.IOException
import java.io.InputStream
import kotlin.math.ceil
import kotlin.math.roundToInt

class AndroidGraphics(private var assets: AssetManager, private var frameBuffer: Bitmap) :
    Graphics {
    private var canvas: Canvas = Canvas(frameBuffer)

    /**
     * Paint for the shapes: rectangles, pixels, and the lines and ovals [Raster] works out, which
     * arrive as runs of rectangles or as polygons traced around them.
     *
     * Antialiasing is turned off by hand. `Paint()` has turned it on by default since Android 12,
     * which gave edges soft pixels on newer phones only, and on no frame the desktop draws.
     */
    private val shapePaint = Paint().apply { isAntiAlias = false }

    /**
     * Paint for text, antialiased on every version: it says so itself rather than taking the
     * default, which was off before Android 12.
     */
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("Arial", Typeface.NORMAL)
    }

    /**
     * Where [drawOval] and [drawOvalOutline] trace their polygons, and the right-hand ends of the
     * rectangles traced so far: an x, a top and a bottom for each. Kept from shape to shape, so that
     * drawing one allocates nothing.
     */
    private val staircase = Path()
    private var stepEnds = IntArray(3 * 128)
    private var steps = 0

    private var srcRect = Rect()
    private var dstRect = Rect()

    /**
     * Paint for [drawPixmapSilhouette], kept next to the color its filter was built for so the
     * filter is only rebuilt when that color actually changes.
     *
     * Nearest-neighbor is set explicitly here. The ordinary blits get it by passing a null paint;
     * this one has to pass a paint, and a filtered one would soften exactly the sprites the rest
     * of the renderer works to keep crisp.
     */
    private val silhouettePaint = Paint().apply { isFilterBitmap = false }
    private var silhouetteRgb = 0

    /** Paint for [drawPixmapFaded]: nothing but an alpha, and no filtering. */
    private val fadePaint = Paint().apply { isFilterBitmap = false }

    override fun newPixmap(filename: String, format: PixmapFormat): Pixmap {
        val config: Bitmap.Config =
            if (format == PixmapFormat.RGB565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        val options = BitmapFactory.Options()
        options.inPreferredConfig = config
        var inputStream: InputStream? = null
        val bitmap: Bitmap?
        try {
            inputStream = assets.open(filename)
            bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap == null) throw RuntimeException(
                "Asset-Bitmap <" + filename
                        + "> not found!"
            )
        } catch (exc: IOException) {
            throw RuntimeException(
                "Asset-Bitmap <" + filename
                        + "> not found!", exc
            )
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (_: IOException) {
                }
            }
        }
        val resultFormat =
            if (bitmap.config == Bitmap.Config.RGB_565) PixmapFormat.RGB565 else PixmapFormat.ARGB8888
        return AndroidPixmap(bitmap, resultFormat)
    }

    override fun clear(color: Int) {
        canvas.drawRGB(
            color and 0xff0000 shr 16, color and 0xff00 shr 8,
            color and 0xff
        )
    }

    override fun drawPixel(x: Int, y: Int, color: Int) {
        shapePaint.color = color
        canvas.drawPoint(x.toFloat(), y.toFloat(), shapePaint)
    }

    // The paint keeps its default fill style for good: nothing strokes with it, which is what lets
    // every run of a line come through here without setting it again.
    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        shapePaint.color = color
        canvas.drawRect(x.toFloat(), y.toFloat(), x + width.toFloat(), y + height.toFloat(), shapePaint)
    }

    /**
     * The pixels [Raster.oval] hands out as rectangles, filled as one polygon traced around them
     * instead: down their left-hand ends and back up their right-hand ends.
     *
     * Every corner of that polygon is a pixel corner, so no pixel's center ever sits on its edge, and
     * it covers exactly the rectangles' pixels. What it saves is Skia's fixed cost for a draw call,
     * paid once an oval rather than once a rectangle: rectangle by rectangle, the sixteen rings of a
     * halo drew at about half the speed.
     */
    override fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int) {
        // A clear color changes nothing, and the outer rings of a faint halo come out clear. Skia
        // would skip the draw by itself, but only after the polygon had been traced for it.
        if (color ushr 24 == 0) return
        staircase.rewind()
        staircase.fillType = Path.FillType.WINDING
        Raster.oval(x, y, width, height, ::traceStep)
        closeStaircase()
        shapePaint.color = color
        canvas.drawPath(staircase, shapePaint)
    }

    /**
     * [Raster.ovalOutline]'s pixels, in one draw call the way [drawOval] fills its own: the oval and
     * its [Raster.ovalInterior] traced as two polygons and filled even-odd, which leaves exactly the
     * ring between them.
     */
    override fun drawOvalOutline(x: Int, y: Int, width: Int, height: Int, color: Int) {
        if (color ushr 24 == 0) return
        staircase.rewind()
        staircase.fillType = Path.FillType.EVEN_ODD
        Raster.oval(x, y, width, height, ::traceStep)
        closeStaircase()
        Raster.ovalInterior(x, y, width, height, ::traceStep)
        closeStaircase()
        shapePaint.color = color
        canvas.drawPath(staircase, shapePaint)
    }

    /** One rectangle of a staircase: its left-hand side traced down, its right-hand end kept. */
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

    /** Back up the right-hand ends of the rectangles traced down so far, closing the polygon. */
    private fun closeStaircase() {
        for (i in steps - 1 downTo 0) {
            val right = stepEnds[i * 3].toFloat()
            staircase.lineTo(right, stepEnds[i * 3 + 2].toFloat())
            staircase.lineTo(right, stepEnds[i * 3 + 1].toFloat())
        }
        if (steps > 0) staircase.close()
        steps = 0
    }

    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int,
        srcWidth: Int, srcHeight: Int
    ) = drawPixmap(pixmap, x, y, srcX, srcY, srcWidth, srcHeight, srcWidth, srcHeight)

    // The null Paint is what keeps a magnified sprite crisp: a default Canvas blit does not
    // filter, so the stretch comes out nearest-neighbor.
    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int,
        srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int
    ) {
        srcRect.left = srcX
        srcRect.top = srcY
        srcRect.right = srcX + srcWidth - 1
        srcRect.bottom = srcY + srcHeight - 1
        dstRect.left = x
        dstRect.top = y
        dstRect.right = x + dstWidth - 1
        dstRect.bottom = y + dstHeight - 1
        canvas.drawBitmap(
            (pixmap as AndroidPixmap).bitmap!!, srcRect, dstRect,
            null
        )
    }

    /**
     * Rotation is applied to the canvas rather than to the bitmap, so the blit underneath is the
     * same one the unrotated path takes - including its `- 1` edges, which keeps a turned sprite
     * the same size as an unturned one.
     */
    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int,
        srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int, rotationDegrees: Float
    ) =
        canvas.withRotation(rotationDegrees, x + dstWidth / 2f, y + dstHeight / 2f) {
            drawPixmap(pixmap, x, y, srcX, srcY, srcWidth, srcHeight, dstWidth, dstHeight)
        }


    /**
     * SRC_IN throws the bitmap's own colors away and keeps its alpha, so what lands is the frame's
     * silhouette in [color]. The paint's alpha then scales the whole thing, which is what makes a
     * flash fade rather than switch off.
     *
     * The filter is rebuilt only when the color actually changes. A flash holds one color for its
     * whole life, so in practice this is built once per run and then reused every frame.
     */
    override fun drawPixmapSilhouette(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int,
        srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int, color: Int
    ) {
        val rgb = color or ALPHA_MASK
        if (rgb != silhouetteRgb) {
            silhouetteRgb = rgb
            silhouettePaint.colorFilter = PorterDuffColorFilter(rgb, PorterDuff.Mode.SRC_IN)
        }
        silhouettePaint.alpha = color ushr 24

        srcRect.left = srcX
        srcRect.top = srcY
        srcRect.right = srcX + srcWidth - 1
        srcRect.bottom = srcY + srcHeight - 1
        dstRect.left = x
        dstRect.top = y
        dstRect.right = x + dstWidth - 1
        dstRect.bottom = y + dstHeight - 1
        canvas.drawBitmap((pixmap as AndroidPixmap).bitmap!!, srcRect, dstRect, silhouettePaint)
    }

    override fun drawPixmapSilhouette(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int,
        srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int, color: Int,
        rotationDegrees: Float
    ) = canvas.withRotation(rotationDegrees, x + dstWidth / 2f, y + dstHeight / 2f) {
        drawPixmapSilhouette(pixmap, x, y, srcX, srcY, srcWidth, srcHeight, dstWidth, dstHeight, color)
    }

    /**
     * The plain blit through a paint carrying the alpha, with the same `- 1` edges. Nearest-neighbor
     * is set on the paint for the reason [silhouettePaint] gives.
     */
    override fun drawPixmapFaded(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int,
        srcWidth: Int, srcHeight: Int, alpha: Float
    ) {
        val steps = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        if (steps <= 0) return
        fadePaint.alpha = steps

        srcRect.left = srcX
        srcRect.top = srcY
        srcRect.right = srcX + srcWidth - 1
        srcRect.bottom = srcY + srcHeight - 1
        dstRect.left = x
        dstRect.top = y
        dstRect.right = x + srcWidth - 1
        dstRect.bottom = y + srcHeight - 1
        canvas.drawBitmap((pixmap as AndroidPixmap).bitmap!!, srcRect, dstRect, fadePaint)
    }

    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) {
        canvas.drawBitmap((pixmap as AndroidPixmap).bitmap!!, x.toFloat(), y.toFloat(), null)
    }

    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) {
        textPaint.textSize = fontSize.toFloat()
        textPaint.color = col
        canvas.drawText(s!!, x.toFloat(), y.toFloat(), textPaint)
    }

    // Rounded up: Android lays text out on fractional advances, and a width rounded down would
    // leave right-aligned text hanging a fraction of a pixel past its edge.
    override fun measureString(s: String, fontSize: Int): Int {
        textPaint.textSize = fontSize.toFloat()
        return ceil(textPaint.measureText(s)).toInt()
    }

    override val width: Int
        get() = frameBuffer.width
    override val height: Int
        get() = frameBuffer.height

    private companion object {
        /** Forces a color opaque, so the paint's own alpha is the only thing scaling the fill. */
        const val ALPHA_MASK = 0xFF000000.toInt()
    }
}