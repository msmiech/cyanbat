package at.smiech.engine

/**
 * A [Pixmap] that is only its pixels, ARGB row by row: what [Graphics.newPixmap] makes from pixels
 * on a test double, where nothing is drawn but the pixels can still be read back.
 */
class PixelPixmap(
    override val width: Int,
    override val height: Int,
    val pixels: IntArray,
) : Pixmap {
    init {
        require(pixels.size >= width * height) { "$width by $height needs ${width * height} pixels, not ${pixels.size}" }
    }

    override fun dispose() = Unit

    override fun readPixels(buffer: IntArray, x: Int, y: Int, width: Int, height: Int): Boolean {
        for (row in 0 until height) {
            pixels.copyInto(
                buffer,
                row * width,
                (y + row) * this.width + x,
                (y + row) * this.width + x + width
            )
        }
        return true
    }
}
