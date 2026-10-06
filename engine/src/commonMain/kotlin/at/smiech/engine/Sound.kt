package at.smiech.engine

/** A short sound effect, loaded whole by [Audio.newSound]. */
interface Sound {
    /** Plays the effect once at [volume], 0 to 1. */
    fun play(volume: Float)

    /** Releases the effect. */
    fun dispose()
}
