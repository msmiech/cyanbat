package at.smiech.engine.math

import kotlin.jvm.JvmInline

/**
 * An immutable 2D vector, two Floats bit-packed into one Long in a value class, so it does not
 * allocate.
 */
@JvmInline
value class Vector2(val packed: Long) {
    constructor(x: Float = 0f, y: Float = 0f) : this(pack(x, y))

    val x: Float get() = unpackX(packed)
    val y: Float get() = unpackY(packed)

    /** This vector with [x] or [y] replaced. */
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
