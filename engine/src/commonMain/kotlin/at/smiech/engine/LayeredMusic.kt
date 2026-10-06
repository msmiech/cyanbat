package at.smiech.engine

/**
 * A piece of music cut into stems that play in lockstep, each at its own level, so the game can
 * build the music up and strip it back while it plays instead of swapping tracks.
 *
 * Level changes land on the music's grid: a layer asked to come in does so on the next beat or bar,
 * so it arrives as part of the music rather than as a fader move; see [Quantum]. The whole mix can
 * also be muffled, as if heard through a wall, to keep the music going under a moment that holds
 * the action without competing for attention.
 *
 * Every stem loops on its own length, and each length is a whole number of bars, so a short drum
 * loop and a long melody stay aligned however many times they repeat.
 */
interface LayeredMusic {
    val layerCount: Int

    /** True between [play] and [pause], whether or not there is a device to hear it on. */
    val isPlaying: Boolean

    /** Starts the music from the top, or resumes it from where [pause] left it. */
    fun play()

    /** Stops the music where it is, for [play] to resume. */
    fun pause()

    /**
     * Sets [layer] to [level], 0 to 1, on the next [quantum] boundary.
     *
     * A layer coming in fades up over [fadeBeats] so it is fully there *on* the boundary, where its
     * first beat lands. A layer going out holds until the boundary and fades from there, so the
     * beat it leaves on still sounds.
     */
    fun setLayerLevel(
        layer: Int,
        level: Float,
        quantum: Quantum = Quantum.BEAT,
        fadeBeats: Float = DEFAULT_FADE_BEATS,
    )

    /**
     * How muffled the whole mix is, from 0 (clear) to 1 (a low-pass filter closed down to little
     * more than the bass). Glides rather than jumps, so a caller can set it every frame.
     */
    fun setMuffle(amount: Float)

    /** The overall level, 0 to 1. Glides like [setMuffle]. */
    fun setVolume(volume: Float)

    /** Stops the music and releases it. */
    fun dispose()

    companion object {
        /** Short enough to land as a hit, long enough not to click. */
        const val DEFAULT_FADE_BEATS = 0.125f
    }
}

/** Where on the music's grid a change to a [LayeredMusic] lands. */
enum class Quantum {
    /** At once, for changes that respond to the player, like a muffle, rather than to the music. */
    IMMEDIATE,

    /** On the next beat: prompt, and still in time. */
    BEAT,

    /** On the next downbeat, for a change the music should be heard to make. */
    BAR,
}

/** The grid a piece of music was written on: its tempo and how its beats group into bars. */
data class MusicGrid(val beatsPerMinute: Double, val beatsPerBar: Int) {
    init {
        require(beatsPerMinute > 0.0) { "A tempo has to be positive, not $beatsPerMinute" }
        require(beatsPerBar > 0) { "A bar needs at least one beat, not $beatsPerBar" }
    }

    /** The length of a beat in audio frames at [sampleRate]. */
    fun framesPerBeat(sampleRate: Int): Double = sampleRate * 60.0 / beatsPerMinute

    /** The length of a bar in audio frames at [sampleRate]. */
    fun framesPerBar(sampleRate: Int): Double = framesPerBeat(sampleRate) * beatsPerBar
}
