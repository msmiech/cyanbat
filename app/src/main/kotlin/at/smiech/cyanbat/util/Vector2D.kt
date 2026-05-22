package at.smiech.cyanbat.util

/**
 * Classic (movement) vector class in two dimensions
 */
data class Vector2D(
    var x: Float = 0f,
    var y: Float = 0f
) {
    operator fun plus(other: Vector2D): Vector2D = Vector2D(x + other.x, y + other.y)
}
