package at.smiech.engine

/**
 * A value computed once per frame of a sprite sheet, and per [variants] of that frame, then kept:
 * a frame's outline, say, or its glint for a light from a given direction.
 *
 * Values are stored by sheet and frame size, one slot per cell of the grid the sheet is cut into,
 * which is how every sprite is addressed, so a lookup is two array reads and allocates nothing. A
 * frame off that grid is computed afresh on every call; nothing the game draws is one.
 *
 * @param make computes a frame's value from the [Pixmap], the frame's corner and size on it, and
 *   the variant. Null is a valid result and is cached like any other.
 */
internal class FrameCache<T : Any>(
    private val variants: Int = 1,
    private val make: (pixmap: Pixmap, x: Int, y: Int, width: Int, height: Int, variant: Int) -> T?,
) {
    private val sheets = HashMap<Pixmap, MutableList<Sheet>>()

    /**
     * The value for the [width] by [height] frame at [x], [y] on [pixmap], computed on first use.
     */
    operator fun get(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        variant: Int = 0
    ): T? {
        if (width <= 0 || height <= 0) return null
        val sheet = sheetOf(pixmap, width, height)
        val column = x / width
        val row = y / height
        if (x % width != 0 || y % height != 0 || column !in 0 until sheet.columns || row !in 0 until sheet.rows) {
            return make(pixmap, x, y, width, height, variant)
        }
        val slot = (row * sheet.columns + column) * variants + variant
        if (!sheet.known[slot]) {
            sheet.values[slot] = make(pixmap, x, y, width, height, variant)
            sheet.known[slot] = true
        }
        @Suppress("UNCHECKED_CAST")
        return sheet.values[slot] as T?
    }

    private fun sheetOf(pixmap: Pixmap, width: Int, height: Int): Sheet {
        val cuts = sheets.getOrPut(pixmap) { ArrayList(1) }
        for (i in cuts.indices) {
            val sheet = cuts[i]
            if (sheet.width == width && sheet.height == height) return sheet
        }
        return Sheet(
            width,
            height,
            pixmap.width / width,
            pixmap.height / height
        ).also { cuts += it }
    }

    /** One sheet cut into [width] by [height] frames, [columns] across and [rows] down. */
    private inner class Sheet(val width: Int, val height: Int, val columns: Int, val rows: Int) {
        val values = arrayOfNulls<Any>(columns * rows * variants)
        val known = BooleanArray(columns * rows * variants)
    }
}
