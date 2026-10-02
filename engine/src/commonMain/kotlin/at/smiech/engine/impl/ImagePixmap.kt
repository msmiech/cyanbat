package at.smiech.engine.impl

import androidx.compose.ui.graphics.ImageBitmap
import at.smiech.engine.Graphics.PixmapFormat
import at.smiech.engine.Pixmap

/**
 * A [Pixmap] as Compose holds a picture: what [ComposeGraphics] loads every asset as, on every
 * platform.
 */
class ImagePixmap(val image: ImageBitmap) : Pixmap {
    override val width: Int get() = image.width
    override val height: Int get() = image.height

    /** Every asset is decoded with alpha, whatever it was asked for as. */
    override val format: PixmapFormat get() = PixmapFormat.ARGB8888

    /** The platform frees the pixels when nothing holds the image any more. */
    override fun dispose() = Unit

    override fun readPixels(buffer: IntArray, x: Int, y: Int, width: Int, height: Int): Boolean {
        image.readPixels(buffer, x, y, width, height)
        return true
    }
}
