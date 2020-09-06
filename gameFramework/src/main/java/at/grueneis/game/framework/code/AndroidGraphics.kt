package at.grueneis.game.framework.code

import android.content.res.AssetManager
import android.graphics.*
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Graphics.PixmapFormat
import at.grueneis.game.framework.Pixmap
import java.io.IOException
import java.io.InputStream

class AndroidGraphics(var assets: AssetManager, var frameBuffer: Bitmap) : Graphics {
    var canvas: Canvas
    var paint: Paint
    var typeface: Typeface
    var srcRect = Rect()
    var dstRect = Rect()
    override fun newPixmap(filename: String, format: PixmapFormat): Pixmap {
        var format = format
        var config: Bitmap.Config? = null // Bitmap.Config
        config = if (format == PixmapFormat.RGB565) Bitmap.Config.RGB_565 else if (format == PixmapFormat.ARGB4444) Bitmap.Config.ARGB_4444 else Bitmap.Config.ARGB_8888
        val options = BitmapFactory.Options()
        options.inPreferredConfig = config
        var `in`: InputStream? = null
        var bitmap: Bitmap? = null
        try {
            `in` = assets.open(filename)
            bitmap = BitmapFactory.decodeStream(`in`)
            if (bitmap == null) throw RuntimeException("Asset-Bitmap <" + filename
                    + "> not found!")
        } catch (exc: IOException) {
            throw RuntimeException("Asset-Bitmap <" + filename
                    + "> not found!")
        } finally {
            if (`in` != null) {
                try {
                    `in`.close()
                } catch (exc: IOException) {
                }
            }
        }
        format = if (bitmap!!.config == Bitmap.Config.RGB_565) PixmapFormat.RGB565 else if (bitmap.config == Bitmap.Config.ARGB_4444) PixmapFormat.ARGB4444 else PixmapFormat.ARGB8888
        return AndroidPixmap(bitmap, format)
    }

    override fun clear(color: Int) {
        canvas.drawRGB(color and 0xff0000 shr 16, color and 0xff00 shr 8,
                color and 0xff)
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

    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int,
                            srcWidth: Int, srcHeight: Int) {
        srcRect.left = srcX
        srcRect.top = srcY
        srcRect.right = srcX + srcWidth - 1
        srcRect.bottom = srcY + srcHeight - 1
        dstRect.left = x
        dstRect.top = y
        dstRect.right = x + srcWidth - 1
        dstRect.bottom = y + srcHeight - 1
        canvas.drawBitmap((pixmap as AndroidPixmap).bitmap!!, srcRect, dstRect,
                null)
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
        canvas.drawOval(RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat()), paint)
    }

    init {
        canvas = Canvas(frameBuffer)
        paint = Paint()
        typeface = Typeface.create("Arial", Typeface.NORMAL)
        paint.typeface = typeface
    }
}