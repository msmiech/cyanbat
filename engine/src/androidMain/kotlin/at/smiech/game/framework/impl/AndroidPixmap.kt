package at.smiech.game.framework.impl

import android.graphics.Bitmap
import at.smiech.engine.Graphics.PixmapFormat
import at.smiech.engine.Pixmap

class AndroidPixmap(var bitmap: Bitmap?, override var format: PixmapFormat) : Pixmap {
    override val width: Int
        get() = bitmap?.width ?: 0
    override val height: Int
        get() = bitmap?.height ?: 0

    override fun dispose() {
        bitmap?.recycle()
    }
}