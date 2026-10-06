package at.smiech.engine.math

/** An immutable axis-aligned rectangle in frame pixels, for entity bounds and hit boxes. */
data class Rect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** Edge-inclusive, so a point exactly on a hit box's border counts as inside. */
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    /** This rectangle moved by [dx], [dy]. */
    fun offset(dx: Float, dy: Float): Rect = Rect(
        left + dx,
        top + dy,
        right + dx,
        bottom + dy
    )

    /** This rectangle grown by [amount] on every side; a negative amount shrinks it. */
    fun inflate(amount: Float): Rect = Rect(
        left - amount,
        top - amount,
        right + amount,
        bottom + amount
    )

    companion object {
        /** The rectangle with its corner at [left], [top] and the given size. */
        fun fromLTWH(left: Float, top: Float, width: Float, height: Float): Rect =
            Rect(left, top, left + width, top + height)
    }
}
