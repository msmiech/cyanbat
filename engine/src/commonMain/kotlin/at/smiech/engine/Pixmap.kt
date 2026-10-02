package at.smiech.engine

interface Pixmap {
    val width: Int
    val height: Int
    val format: Graphics.PixmapFormat
    fun dispose()

    /**
     * Copies the [width] by [height] block of the picture at [x], [y] into [buffer], ARGB and a row
     * at a time; false, with nothing copied, for a pixmap that has no pixels to read, as a test
     * double has none.
     *
     * For working a shape out of the art, such as the outline a shadow is cast in. Nothing is drawn
     * through it.
     */
    fun readPixels(buffer: IntArray, x: Int, y: Int, width: Int, height: Int): Boolean = false
}
