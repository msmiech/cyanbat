package at.smiech.engine

import kotlin.math.roundToInt

/**
 * Packed ARGB colors and color helpers, matching what [Graphics] takes, so screen code does not
 * depend on a platform's color class.
 */
object EngineColors {
    const val BLACK: Int = 0xFF000000.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val CYAN: Int = 0xFF00FFFF.toInt()
    const val YELLOW: Int = 0xFFFFFF00.toInt()
    const val RED: Int = 0xFFFF0000.toInt()

    /**
     * A shield bubble: pale periwinkle. Cool, so it reads as a barrier rather than as part of the
     * warm enemy inside it, and violet enough not to be mistaken for the bat's cyan.
     */
    const val SHIELD: Int = 0xFFB8C4FF.toInt()

    /** [color] with its alpha replaced by [alpha], where alpha runs 0..1. */
    fun withAlpha(color: Int, alpha: Float): Int {
        val opacity = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        return (color and 0x00FFFFFF) or (opacity shl 24)
    }

    /**
     * [color] with its own alpha scaled by [factor], where [factor] runs 0..1.
     *
     * Unlike [withAlpha], this keeps the opacity the color was authored at, so a translucent fill
     * does not turn solid when something fades it in.
     */
    fun scaleAlpha(color: Int, factor: Float): Int {
        val scaled = ((color ushr 24) * factor.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (scaled shl 24)
    }

    /**
     * [from] blended toward [to], where [t] runs 0..1.
     *
     * Channel by channel in sRGB: not physically correct, but what a ramp between two hand-picked
     * palette colors wants. Alpha is interpolated too, so a fade to a transparent color works.
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
