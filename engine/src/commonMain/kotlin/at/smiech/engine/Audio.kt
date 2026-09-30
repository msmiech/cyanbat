package at.smiech.engine

interface Audio {
    /**
     * A piece of music played straight through, from an IMA ADPCM WAV like the stems of
     * [newLayeredMusic], and decoded in common code the way they are: so it loops without a gap and
     * sounds the same on every platform. See [Music].
     */
    fun newMusic(filename: String): Music

    fun newSound(filename: String): Sound

    /**
     * Music built from [stems], bottom layer first, that play together on [grid]; see
     * [LayeredMusic]. Every stem is an IMA ADPCM WAV of the same sample rate.
     *
     * The stems are decoded as they play, in common code, so they sound the same, and line up to
     * the sample, on every platform: a platform decoder would pad or trim the ends of a compressed
     * file in its own way, and stems a few milliseconds apart are a flam on every drum hit.
     */
    fun newLayeredMusic(stems: List<String>, grid: MusicGrid): LayeredMusic

    fun dispose()
}
