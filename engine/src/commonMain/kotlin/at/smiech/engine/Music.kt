package at.smiech.engine

/**
 * A piece of music played straight through: the menu's, looping for as long as the menu is open,
 * or the game over's, played once. See [LayeredMusic] for music the game builds up and strips back.
 */
interface Music {
    /**
     * Starts the music, or carries on from where [pause] left it. Music that has played to its end
     * starts again from the top.
     */
    fun play()

    fun pause()

    /** The overall level, 0 to 1. */
    fun setVolume(volume: Float)

    /** True between [play] and [pause], until music that does not loop has played to its end. */
    val isPlaying: Boolean

    /** Whether the music goes round again at its end, or stops there. Off to begin with. */
    var isLooping: Boolean

    fun dispose()
}
