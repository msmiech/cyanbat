package at.smiech.engine.impl

import at.smiech.engine.Graphics
import at.smiech.engine.Graphics.PixmapFormat
import at.smiech.engine.Pixmap
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * [Graphics] drawing into an offscreen [BufferedImage] framebuffer, mirroring the Android
 * implementation so both platforms produce the same pixels.
 *
 * @param assetStream resolves an asset filename to its bytes (classpath resources on desktop).
 */
class DesktopGraphics(
    val frameBuffer: BufferedImage,
    private val assetStream: (String) -> java.io.InputStream,
) : Graphics {

    private val g2d = frameBuffer.createGraphics().apply {
        // Pixel art: never smooth it.
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
    }

    override fun newPixmap(filename: String, format: PixmapFormat): Pixmap {
        val decoded = assetStream(filename).use { ImageIO.read(it) }
            ?: throw RuntimeException("Asset-Bitmap <$filename> not found!")

        // Redraw into a known type so downstream blits do not hit a surprising colour model.
        val type = if (format == PixmapFormat.RGB565) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB
        val image = BufferedImage(decoded.width, decoded.height, type)
        image.createGraphics().apply {
            drawImage(decoded, 0, 0, null)
            dispose()
        }
        return DesktopPixmap(image, format)
    }

    // Matches AndroidGraphics.clear, which goes through drawRGB and so discards alpha.
    override fun clear(color: Int) {
        g2d.color = Color(color and 0xFFFFFF)
        g2d.fillRect(0, 0, frameBuffer.width, frameBuffer.height)
    }

    override fun drawPixel(x: Int, y: Int, color: Int) {
        frameBuffer.setRGB(x, y, color)
    }

    override fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int) {
        g2d.color = Color(color, true)
        g2d.drawLine(xFrom, yFrom, xTo, yTo)
    }

    // The Android implementation sets Paint.Style.FILL, so these are filled shapes despite the name.
    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        g2d.color = Color(color, true)
        g2d.fillRect(x, y, width, height)
    }

    override fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int) {
        g2d.color = Color(color, true)
        g2d.fillOval(x, y, width, height)
    }

    /**
     * The `- 1` on the far edges is deliberate and matches AndroidGraphics exactly. Android's
     * `Rect` and Java2D's `sx2`/`dx2` are both exclusive, so the Android code already draws one
     * column and row short of each sprite. Reproducing it keeps desktop pixel-identical; a "fix"
     * here would shift every sprite relative to Android.
     */
    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int
    ) {
        g2d.drawImage(
            (pixmap as DesktopPixmap).image,
            x, y, x + srcWidth - 1, y + srcHeight - 1,
            srcX, srcY, srcX + srcWidth - 1, srcY + srcHeight - 1,
            null
        )
    }

    /** Same `- 1` convention as the unscaled blit above, on both the source and the destination. */
    override fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
    ) {
        g2d.drawImage(
            (pixmap as DesktopPixmap).image,
            x, y, x + dstWidth - 1, y + dstHeight - 1,
            srcX, srcY, srcX + srcWidth - 1, srcY + srcHeight - 1,
            null
        )
    }

    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) {
        g2d.drawImage((pixmap as DesktopPixmap).image, x, y, null)
    }

    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) {
        if (s == null) return
        g2d.color = Color(col, true)
        g2d.font = Font(Font.SANS_SERIF, Font.PLAIN, fontSize)
        // Both Android and Java2D draw text from the baseline, so y transfers unchanged.
        g2d.drawString(s, x, y)
    }

    override val width: Int get() = frameBuffer.width
    override val height: Int get() = frameBuffer.height
}
