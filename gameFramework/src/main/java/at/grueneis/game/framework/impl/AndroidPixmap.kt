package at.grueneis.game.framework.impl

import android.graphics.Bitmap
import at.grueneis.game.framework.Graphics.PixmapFormat
import at.grueneis.game.framework.Pixmap

class AndroidPixmap(var bitmap: Bitmap?, override var format: PixmapFormat) : Pixmap {
    override val width: Int
        get() = bitmap?.width ?: 0
    override val height: Int
        get() = bitmap?.height ?: 0

    override fun dispose() {
        bitmap?.recycle()
    }
}