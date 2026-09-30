package at.smiech.engine

/**
 * A piece of music cut into stems that play in lockstep, each at a level of its own, so a game can
 * build the music up and strip it back while it plays - the way Doom and SSX 3 score their action,
 * rather than swapping one finished track for another.
 *
 * Levels change on the music's own grid. A layer asked to come in does so on the next beat or bar,
 * so it arrives as part of the music instead of as a volume knob being turned; see [Quantum]. The
 * whole mix can also be muffled, as if heard through a wall, which is how a moment that holds the
 * action still keeps the music going without it competing for attention.
 *
 * Every stem loops on its own length, and each length is a whole number of bars, so a short drum
 * loop and a long melody stay aligned however many times round they have been.
 */
interface LayeredMusic {
    val layerCount: Int

    /** True between [play] and [pause], whether or not there is a device to hear it on. */
    val isPlaying: Boolean

    /** Starts the music from the top, or carries on from where [pause] left it. */
    fun play()

    fun pause()

    /**
     * Sets [layer] to [level], 0 to 1, on the next [quantum] boundary.
     *
     * A layer coming in is faded up over [fadeBeats] so that it is fully there *on* the boundary,
     * where the beat it arrives on lands. A layer going out holds until the boundary and fades
     * from it, so the beat it is leaving on still sounds.
     */
    fun setLayerLevel(
        layer: Int,
        level: Float,
        quantum: Quantum = Quantum.BEAT,
        fadeBeats: Float = DEFAULT_FADE_BEATS,
    )

    /**
     * How muffled the whole mix is, from 0 (clear) to 1 (a low-pass filter closed down to little
     * more than the bass). Glides rather than jumps, so a caller can set it once a frame.
     */
    fun setMuffle(amount: Float)

    /** The overall level, 0 to 1. Glides like [setMuffle]. */
    fun setVolume(volume: Float)

    fun dispose()

    companion object {
        /** Short enough to land as a hit, long enough not to click. */
        const val DEFAULT_FADE_BEATS = 0.125f
    }
}

/** Where on the music's grid a change to a [LayeredMusic] lands. */
enum class Quantum {
    /** At once. For changes that answer the player, like a muffle, rather than the music. */
    IMMEDIATE,

    /** On the next beat: prompt, and still in time. */
    BEAT,

    /** On the next downbeat, for a change the music should be seen to make. */
    BAR,
}

/** The grid a piece of music was written on: how fast its beats fall and how they group into bars. */
data class MusicGrid(val beatsPerMinute: Double, val beatsPerBar: Int) {
    init {
        require(beatsPerMinute > 0.0) { "A tempo has to be positive, not $beatsPerMinute" }
        require(beatsPerBar > 0) { "A bar needs at least one beat, not $beatsPerBar" }
    }

    fun framesPerBeat(sampleRate: Int): Double = sampleRate * 60.0 / beatsPerMinute

    fun framesPerBar(sampleRate: Int): Double = framesPerBeat(sampleRate) * beatsPerBar
}
