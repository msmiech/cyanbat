package at.smiech.engine

/**
 * Short vibration feedback. Platforms without a vibrator supply a no-op implementation rather
 * than the game branching on which platform it is running on.
 */
fun interface Haptics {
    fun vibrate(durationMillis: Long)

    companion object {
        val None: Haptics = Haptics { }
    }
}
