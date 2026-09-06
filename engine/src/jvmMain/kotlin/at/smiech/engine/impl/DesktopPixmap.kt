package at.smiech.engine.impl

import at.smiech.engine.Graphics.PixmapFormat
import at.smiech.engine.Pixmap
import java.awt.image.BufferedImage

/** [Pixmap] backed by an AWT [BufferedImage]. */
class DesktopPixmap(val image: BufferedImage, override val format: PixmapFormat) : Pixmap {
    override val width: Int get() = image.width
    override val height: Int get() = image.height

    // AWT has no explicit native-memory release like Bitmap.recycle(); flush() drops any
    // cached rendering data, which is the closest equivalent.
    override fun dispose() = image.flush()
}
