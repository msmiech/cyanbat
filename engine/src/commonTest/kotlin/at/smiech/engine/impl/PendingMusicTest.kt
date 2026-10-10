package at.smiech.engine.impl

import at.smiech.engine.LayeredMusic
import at.smiech.engine.Music
import at.smiech.engine.Quantum
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [PendingLayeredMusic] and [PendingMusic]: music told what to do before it has arrived. */
class PendingMusicTest {

    /** Remembers what it was last told, and how. */
    private class Layers(override val layerCount: Int = 3) : LayeredMusic {
        val levels = FloatArray(layerCount) { -1f }
        val quanta = arrayOfNulls<Quantum>(layerCount)
        var lastMuffle = -1f
        var lastVolume = -1f
        var disposed = false
        override var isPlaying = false
        override fun play() {
            isPlaying = true
        }

        override fun pause() {
            isPlaying = false
        }

        override fun setLayerLevel(layer: Int, level: Float, quantum: Quantum, fadeBeats: Float) {
            levels[layer] = level
            quanta[layer] = quantum
        }

        override fun setMuffle(amount: Float) {
            lastMuffle = amount
        }

        override fun setVolume(volume: Float) {
            lastVolume = volume
        }

        override fun dispose() {
            disposed = true
        }
    }

    private class Track : Music {
        var lastVolume = -1f
        var disposed = false
        override var isPlaying = false
        override var isLooping = false
        override fun play() {
            isPlaying = true
        }

        override fun pause() {
            isPlaying = false
        }

        override fun setVolume(volume: Float) {
            lastVolume = volume
        }

        override fun dispose() {
            disposed = true
        }
    }

    @Test
    fun `music that arrives takes the latest of everything it was told and plays at once`() {
        val pending = PendingLayeredMusic(3)
        pending.setLayerLevel(0, 1f, Quantum.BAR, 0.5f)
        pending.setLayerLevel(2, 0.5f, Quantum.BEAT, 0.5f)
        pending.setLayerLevel(2, 0.75f, Quantum.BEAT, 0.5f)
        pending.setMuffle(0.4f)
        pending.setVolume(0.6f)
        pending.play()
        assertTrue(pending.isPlaying, "playing, as far as the game can tell")

        val music = Layers()
        pending.arrive(music)

        assertContentEquals(floatArrayOf(1f, 0f, 0.75f), music.levels)
        assertTrue(music.quanta.all { it == Quantum.IMMEDIATE }, "nothing to wait for a beat of")
        assertEquals(0.4f, music.lastMuffle)
        assertEquals(0.6f, music.lastVolume)
        assertTrue(music.isPlaying)
    }

    @Test
    fun `music that has arrived is told everything as it happens`() {
        val pending = PendingLayeredMusic(3)
        val music = Layers()
        pending.arrive(music)
        assertFalse(music.isPlaying, "never asked to play")

        pending.play()
        pending.setLayerLevel(1, 1f, Quantum.BAR, 0.5f)
        assertTrue(music.isPlaying)
        assertEquals(1f, music.levels[1])
        assertEquals(Quantum.BAR, music.quanta[1])
        pending.pause()
        assertFalse(pending.isPlaying)
        pending.dispose()
        assertTrue(music.disposed)
    }

    @Test
    fun `music that arrives too late is disposed of`() {
        val pending = PendingLayeredMusic(3)
        pending.play()
        pending.dispose()
        assertFalse(pending.isPlaying)
        val music = Layers()
        pending.arrive(music)
        assertTrue(music.disposed)
        assertFalse(music.isPlaying)
    }

    @Test
    fun `music has to arrive with as many layers as it was made for`() {
        assertFailsWith<IllegalArgumentException> { PendingLayeredMusic(2).arrive(Layers(3)) }
    }

    @Test
    fun `a track that arrives loops and plays as loud as it was told`() {
        val pending = PendingMusic()
        pending.isLooping = true
        pending.setVolume(0.3f)
        pending.play()
        val track = Track()
        pending.arrive(track)
        assertTrue(track.isLooping)
        assertEquals(0.3f, track.lastVolume)
        assertTrue(track.isPlaying)

        pending.dispose()
        assertTrue(track.disposed)
        val late = Track()
        pending.arrive(late)
        assertTrue(late.disposed)
    }

    @Test
    fun `a track paused before it arrives arrives paused`() {
        val pending = PendingMusic()
        pending.play()
        pending.pause()
        val track = Track()
        pending.arrive(track)
        assertFalse(track.isPlaying)
    }
}
