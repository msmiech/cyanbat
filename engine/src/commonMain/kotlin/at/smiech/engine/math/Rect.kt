package at.smiech.engine.math

/**
 * Immutable Float-based rectangle for game geometry.
 */
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

    fun intersects(other: Rect): Boolean {
        return left < other.right && right > other.left &&
                top < other.bottom && bottom > other.top
    }

    /** Edge-inclusive, so a point landing exactly on a hit box's border still counts as inside. */
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    fun offset(dx: Float, dy: Float): Rect = Rect(
        left + dx,
        top + dy,
        right + dx,
        bottom + dy
    )

    fun inflate(amount: Float): Rect = Rect(
        left - amount,
        top - amount,
        right + amount,
        bottom + amount
    )

    companion object {
        val Empty = Rect()
        
        fun fromLTRB(left: Float, top: Float, right: Float, bottom: Float): Rect = 
            Rect(left, top, right, bottom)
            
        fun fromLTWH(left: Float, top: Float, width: Float, height: Float): Rect = 
            Rect(left, top, left + width, top + height)
    }
}
