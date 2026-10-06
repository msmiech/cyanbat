package at.smiech.engine

/** An image loaded through [Graphics.newPixmap], drawn whole or a frame at a time. */
interface Pixmap {
    val width: Int
    val height: Int
    fun dispose()

    /**
     * Copies the [width] by [height] block at [x], [y] into [buffer] as ARGB, row by row. Returns
     * false, copying nothing, for a pixmap without readable pixels, such as a test double.
     *
     * Used to derive shapes from the art, such as the outline a shadow is cast
     * from; nothing is drawn through it.
     */
    fun readPixels(buffer: IntArray, x: Int, y: Int, width: Int, height: Int): Boolean = false
}
