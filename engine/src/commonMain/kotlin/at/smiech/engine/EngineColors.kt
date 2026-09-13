package at.smiech.engine

import kotlin.math.roundToInt

/**
 * Packed ARGB colors, matching what [Graphics] takes. These exist so screen code does not need
 * `android.graphics.Color`, which would pin it to one platform.
 */
object EngineColors {
    const val BLACK: Int = 0xFF000000.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val CYAN: Int = 0xFF00FFFF.toInt()
    const val YELLOW: Int = 0xFFFFFF00.toInt()
    const val RED: Int = 0xFFFF0000.toInt()

    /**
     * [color] at [alpha] of its opacity, where alpha runs 0..1. Both platforms honor the alpha
     * byte in every draw call, so this is all a fade needs.
     */
    fun withAlpha(color: Int, alpha: Float): Int {
        val opacity = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        return (color and 0x00FFFFFF) or (opacity shl 24)
    }

    /**
     * [from] blended toward [to], where [t] runs 0..1.
     *
     * Channel by channel in sRGB, which is not physically correct but is what a palette ramp
     * between two neighboring hues wants: the point is to walk a gradient somebody picked by eye,
     * not to be right about light. Alpha is interpolated with the rest, so a fade to a transparent
     * color works.
     */
    fun lerp(from: Int, to: Int, t: Float): Int {
        val amount = t.coerceIn(0f, 1f)
        var result = 0
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val a = (from ushr shift) and 0xFF
            val b = (to ushr shift) and 0xFF
            result = result or (((a + (b - a) * amount).roundToInt() and 0xFF) shl shift)
        }
        return result
    }
}
