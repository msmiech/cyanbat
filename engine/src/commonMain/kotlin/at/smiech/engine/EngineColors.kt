package at.smiech.engine

/**
 * Packed ARGB colours, matching what [Graphics] takes. These exist so screen code does not need
 * `android.graphics.Color`, which would pin it to one platform.
 */
object EngineColors {
    const val BLACK: Int = 0xFF000000.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val CYAN: Int = 0xFF00FFFF.toInt()
    const val YELLOW: Int = 0xFFFFFF00.toInt()
}
