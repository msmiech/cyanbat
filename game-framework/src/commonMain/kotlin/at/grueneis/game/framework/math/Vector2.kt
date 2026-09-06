package at.grueneis.game.framework.math

import kotlin.jvm.JvmInline

/**
 * Immutable 2D vector optimized as a value class to eliminate heap allocation.
 * Uses bit-packing to store two Floats in a single Long.
 */
@JvmInline
value class Vector2(val packed: Long) {
    constructor(x: Float = 0f, y: Float = 0f) : this(pack(x, y))

    val x: Float get() = unpackX(packed)
    val y: Float get() = unpackY(packed)

    operator fun plus(other: Vector2): Vector2 = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2): Vector2 = Vector2(x - other.x, y - other.y)
    operator fun times(scale: Float): Vector2 = Vector2(x * scale, y * scale)

    fun copy(x: Float = this.x, y: Float = this.y): Vector2 = Vector2(x, y)

    override fun toString(): String = "Vector2(x=$x, y=$y)"

    companion object {
        val Zero = Vector2(0f, 0f)

        private fun pack(x: Float, y: Float): Long {
            val xi = x.toBits().toLong()
            val yi = y.toBits().toLong()
            return (xi shl 32) or (yi and 0xFFFFFFFFL)
        }
        private fun unpackX(packed: Long): Float = Float.fromBits((packed shr 32).toInt())
        private fun unpackY(packed: Long): Float = Float.fromBits(packed.toInt())
    }
}
