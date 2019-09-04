package at.smiech.cyanbat.util

/**
 * Classic (movement) vector class in two dimensions
 */
class Vector2D {
    var x: Float = 0.toFloat()
    var y: Float = 0.toFloat()

    constructor() {
        x = 0f
        y = 0f
    }

    constructor(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    fun add(value: Vector2D) {
        x += value.x
        y += value.y
    }

    fun subtract(value: Vector2D) {
        x -= value.x
        y -= value.y
    }

}
