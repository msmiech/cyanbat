package at.smiech.engine

/**
 * Loads the game's sounds and music. Each platform supplies one, backed by its own audio output.
 */
interface Audio {
    /**
     * A single track, decoded from an IMA ADPCM WAV in common code like the stems of
     * [newLayeredMusic], so it loops without a gap and sounds the same on every platform.
     */
    fun newMusic(filename: String): Music

    /** A short effect, loaded whole so it can be played at once and repeatedly. */
    fun newSound(filename: String): Sound

    /**
     * Music built from [stems], bottom layer first, played together on [grid]; see [LayeredMusic].
     * Every stem is an IMA ADPCM WAV of the same sample rate.
     *
     * The stems are decoded in common code as they play. A platform decoder pads or
     * trims the ends of a compressed file in its own way, and stems a few
     * milliseconds apart flam on every drum hit.
     */
    fun newLayeredMusic(stems: List<String>, grid: MusicGrid): LayeredMusic

    /** Releases every sound and piece of music created here. */
    fun dispose()
}
