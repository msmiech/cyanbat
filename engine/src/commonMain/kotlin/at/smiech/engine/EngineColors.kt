package at.smiech.engine

import kotlin.math.roundToInt

/**
 * Packed ARGB colours, matching what [Graphics] takes. These exist so screen code does not need
 * `android.graphics.Color`, which would pin it to one platform.
 */
object EngineColors {
    const val BLACK: Int = 0xFF000000.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val CYAN: Int = 0xFF00FFFF.toInt()
    const val YELLOW: Int = 0xFFFFFF00.toInt()
    const val RED: Int = 0xFFFF0000.toInt()

    /**
     * [color] at [alpha] of its opacity, where alpha runs 0..1. Both platforms honour the alpha
     * byte in every draw call, so this is all a fade needs.
     */
    fun withAlpha(color: Int, alpha: Float): Int {
        val opacity = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        return (color and 0x00FFFFFF) or (opacity shl 24)
    }
}
