package at.smiech.engine.impl

import at.smiech.engine.LayeredMusic
import at.smiech.engine.Music
import at.smiech.engine.MusicGrid
import at.smiech.engine.Quantum

/**
 * A piece of music played straight through, on the machinery of a stage's layered music: its one
 * stem decoded in common code by a [StemMixer], and handed to the device by the platform's
 * [LayeredMusic], with its only layer all the way up.
 *
 * So the menu's and the game over's music is decoded exactly as the stages' is, with no codec in
 * the way: the menu's loop comes round without a gap, and the game over's track, which does not
 * loop, stops at its end.
 *
 * @param player plays [mixer]'s output; this track owns it, and disposes of it.
 */
class TrackMusic(private val mixer: StemMixer, private val player: LayeredMusic) : Music {
    init {
        require(mixer.layerCount == 1) { "A track is one stem, not ${mixer.layerCount}" }
        mixer.isLooping = false
        player.setLayerLevel(0, 1f, Quantum.IMMEDIATE, fadeBeats = 0f)
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun setVolume(volume: Float) = player.setVolume(volume)

    override val isPlaying: Boolean get() = player.isPlaying

    override var isLooping: Boolean
        get() = mixer.isLooping
        set(looping) {
            mixer.isLooping = looping
        }

    override fun dispose() = player.dispose()

    companion object {
        /** A track never moves its layer on the beat, so any grid serves: a beat a second. */
        private val GRID = MusicGrid(beatsPerMinute = 60.0, beatsPerBar = 1)

        /** A track of [clip], played by whatever [play] builds around its mixer. */
        fun of(clip: ImaAdpcmClip, play: (StemMixer) -> LayeredMusic): TrackMusic {
            val mixer = StemMixer(listOf(clip), GRID)
            return TrackMusic(mixer, play(mixer))
        }
    }
}
