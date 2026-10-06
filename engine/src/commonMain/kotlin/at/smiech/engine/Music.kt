package at.smiech.engine

/**
 * A single track played straight through, such as the menu's loop or the game over's tune. See
 * [LayeredMusic] for music the game builds up and strips back while it plays.
 */
interface Music {
    /**
     * Starts the music, or resumes it from where [pause] left it. Music that has played to its end
     * starts again from the top.
     */
    fun play()

    /** Stops the music where it is, for [play] to resume. */
    fun pause()

    /** The overall level, 0 to 1. */
    fun setVolume(volume: Float)

    /** True between [play] and [pause], until music that does not loop has played to its end. */
    val isPlaying: Boolean

    /** Whether the music starts over at its end or stops there. Off by default. */
    var isLooping: Boolean

    /** Stops the music and releases it. */
    fun dispose()
}
