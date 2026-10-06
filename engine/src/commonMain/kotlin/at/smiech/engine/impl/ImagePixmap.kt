package at.smiech.engine.impl

import androidx.compose.ui.graphics.ImageBitmap
import at.smiech.engine.Pixmap

/** A [Pixmap] backed by a Compose [ImageBitmap]; [ComposeGraphics] loads every asset as one. */
class ImagePixmap(val image: ImageBitmap) : Pixmap {
    override val width: Int get() = image.width
    override val height: Int get() = image.height

    /** The platform frees the pixels once nothing holds the image. */
    override fun dispose() = Unit

    override fun readPixels(buffer: IntArray, x: Int, y: Int, width: Int, height: Int): Boolean {
        image.readPixels(buffer, x, y, width, height)
        return true
    }
}
