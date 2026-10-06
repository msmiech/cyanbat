package at.smiech.engine

/**
 * Short vibration feedback. Platforms without a vibrator supply [None] rather than the game
 * branching on the platform.
 */
fun interface Haptics {
    /** Vibrates once for [durationMillis]. */
    fun vibrate(durationMillis: Long)

    companion object {
        val None: Haptics = Haptics { }
    }
}
