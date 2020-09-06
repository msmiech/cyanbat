package at.grueneis.game.framework

import at.grueneis.game.framework.Graphics.PixmapFormat

interface Pixmap {
    val width: Int
    val height: Int
    val format: PixmapFormat
    fun dispose()
}