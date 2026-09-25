package at.smiech.engine.impl

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.withRotation
import at.smiech.engine.Graphics
import at.smiech.engine.Graphics.PixmapFormat
import at.smiech.engine.Pixmap
import java.io.IOException
import java.io.InputStream

class AndroidGraphics(private var assets: AssetManager, private var frameBuffer: Bitmap) :
    Graphics {
    private var canvas: Canvas = Canvas(frameBuffer)
    private var paint: Paint = Paint()
    private var typeface: Typeface = Typeface.create("Arial", Typeface.NORMAL)
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
        paint.color = color
        canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
    }

    override fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int) {
        paint.color = color
        canvas.drawLine(xFrom.toFloat(), yFrom.toFloat(), xTo.toFloat(), yTo.toFloat(), paint)
    }

    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRect(x.toFloat(), y.toFloat(), x + width.toFloat(), y + height.toFloat(), paint)
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

    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) {
        canvas.drawBitmap((pixmap as AndroidPixmap).bitmap!!, x.toFloat(), y.toFloat(), null)
    }

    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) {
        paint.textSize = fontSize.toFloat()
        paint.color = col
        canvas.drawText(s!!, x.toFloat(), y.toFloat(), paint)
    }

    override val width: Int
        get() = frameBuffer.width
    override val height: Int
        get() = frameBuffer.height

    override fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawOval(
            RectF(
                x.toFloat(),
                y.toFloat(),
                (x + width).toFloat(),
                (y + height).toFloat()
            ), paint
        )
    }

    override fun drawOvalOutline(x: Int, y: Int, width: Int, height: Int, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawOval(
            RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat()),
            paint
        )
        // Put back: drawString and drawLine do not set a style of their own, and text drawn with
        // a stroking paint comes out as hollow outlines.
        paint.style = Paint.Style.FILL
    }

    init {
        paint.typeface = typeface
    }

    private companion object {
        /** Forces a color opaque, so the paint's own alpha is the only thing scaling the fill. */
        const val ALPHA_MASK = 0xFF000000.toInt()
    }
}