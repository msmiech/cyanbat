package at.smiech.engine

interface Pixmap {
    val width: Int
    val height: Int
    val format: Graphics.PixmapFormat
    fun dispose()
}
