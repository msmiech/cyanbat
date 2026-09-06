package at.smiech.cyanbat.util

/**
 * Classic (movement) vector class in two dimensions.
 * Optimized as a value class to eliminate heap allocation in high-frequency loops.
 * Uses bit-packing to satisfy the single-property requirement for @JvmInline.
 */
@JvmInline
value class Vector2D(val packed: Long) {
    constructor(x: Float = 0f, y: Float = 0f) : this(pack(x, y))

    val x: Float get() = unpackX(packed)
    val y: Float get() = unpackY(packed)

    operator fun plus(other: Vector2D): Vector2D = Vector2D(x + other.x, y + other.y)

    fun copy(x: Float = this.x, y: Float = this.y): Vector2D = Vector2D(x, y)

    override fun toString(): String = "Vector2D(x=$x, y=$y)"

    companion object {
        private fun pack(x: Float, y: Float): Long {
            val xi = x.toBits().toLong()
            val yi = y.toBits().toLong()
            return (xi shl 32) or (yi and 0xFFFFFFFFL)
        }
        private fun unpackX(packed: Long): Float = Float.fromBits((packed shr 32).toInt())
        private fun unpackY(packed: Long): Float = Float.fromBits(packed.toInt())
    }
}
