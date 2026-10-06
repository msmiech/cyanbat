package at.smiech.engine.impl

import at.smiech.engine.LayeredMusic
import at.smiech.engine.Quantum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [TrackMusic]: a single track on the mixer, looping or played once. */
class TrackMusicTest {

    /** Plays nothing: hands the track's orders to its mixer, as a platform's player does. */
    private class Player(private val mixer: StemMixer) : LayeredMusic {
        var disposed = false
        override val layerCount = mixer.layerCount
        override var isPlaying = false
        override fun play() {
            isPlaying = true
        }

        override fun pause() {
            isPlaying = false
        }

        override fun setLayerLevel(layer: Int, level: Float, quantum: Quantum, fadeBeats: Float) =
            mixer.setLayerLevel(layer, level, quantum, fadeBeats)

        override fun setMuffle(amount: Float) = mixer.setMuffle(amount)
        override fun setVolume(volume: Float) = mixer.setVolume(volume)
        override fun dispose() {
            disposed = true
        }
    }

    private fun track(frames: Int): Pair<TrackMusic, StemMixer> {
        lateinit var mixer: StemMixer
        val track = TrackMusic.of(constantClip(8192, frames = frames, sampleRate = 8000)) {
            mixer = it
            Player(it)
        }
        return track to mixer
    }

    private fun render(mixer: StemMixer, frames: Int): FloatArray {
        val pcm = ShortArray(frames * 2)
        mixer.render(pcm, frames)
        return FloatArray(frames) { pcm[2 * it] / 32767f }
    }

    @Test
    fun `a track plays at full and once unless it is told to loop`() {
        val (track, mixer) = track(frames = 4000)
        assertFalse(track.isLooping)
        val out = render(mixer, 5000)
        assertEquals(0.25f, out[2000], 0.001f)
        assertEquals(0f, out[4500], "once through, then silence")
    }

    @Test
    fun `a looping track comes round again`() {
        val (track, mixer) = track(frames = 4000)
        track.isLooping = true
        val out = render(mixer, 5000)
        assertEquals(0.25f, out[4500], 0.001f)
    }

    @Test
    fun `a track hands playing and disposing to its player`() {
        lateinit var player: Player
        val track = TrackMusic.of(constantClip(8192, frames = 4000, sampleRate = 8000)) {
            Player(it).also { made -> player = made }
        }
        track.play()
        assertTrue(track.isPlaying)
        track.pause()
        assertFalse(track.isPlaying)
        track.dispose()
        assertTrue(player.disposed)
    }
}
