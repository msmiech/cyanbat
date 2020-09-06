package at.grueneis.game.framework

interface Graphics {
    enum class PixmapFormat {
        ARGB8888, ARGB4444, RGB565
    }

    fun newPixmap(filename: String, format: PixmapFormat): Pixmap
    fun clear(color: Int)
    fun drawPixel(x: Int, y: Int, color: Int)
    fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int)
    fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int)
    fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int)
    fun drawPixmap(pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int)
    fun drawPixmap(pixmap: Pixmap, x: Int, y: Int)
    fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int)
    val width: Int
    val height: Int
}