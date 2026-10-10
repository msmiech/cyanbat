package at.smiech.engine.impl

import at.smiech.engine.LayeredMusic
import at.smiech.engine.Music
import at.smiech.engine.Quantum

/**
 * Layered music whose stems are still on their way, as a browser's are: fetched when the music is
 * made, rather than with the rest of the assets, since a stage's stems are megabytes. It takes
 * everything the game asks of it at once, and hands it on to the music that [arrive]s, so a run
 * starts without waiting on the download. Until then it is silent.
 *
 * Only the latest of each setting is handed on, and at once: a layer asked in on a beat before the
 * music arrived has no beat to land on.
 */
class PendingLayeredMusic(override val layerCount: Int) : LayeredMusic {
    private var music: LayeredMusic? = null
    private val levels = FloatArray(layerCount)
    private var muffle = 0f
    private var volume = 1f
    private var playing = false
    private var disposed = false

    override val isPlaying: Boolean get() = music?.isPlaying ?: playing

    /**
     * Hands every setting so far to [arrived] and plays it if this is playing; from then on, this
     * passes everything straight to it. Music that arrives after a [dispose] is disposed of.
     */
    fun arrive(arrived: LayeredMusic) {
        require(arrived.layerCount == layerCount) {
            "Expected $layerCount layers, but ${arrived.layerCount} arrived"
        }
        if (disposed || music != null) {
            arrived.dispose()
            return
        }
        for (layer in levels.indices) {
            arrived.setLayerLevel(layer, levels[layer], Quantum.IMMEDIATE, fadeBeats = 0f)
        }
        arrived.setMuffle(muffle)
        arrived.setVolume(volume)
        if (playing) arrived.play()
        music = arrived
    }

    override fun play() {
        if (disposed) return
        playing = true
        music?.play()
    }

    override fun pause() {
        playing = false
        music?.pause()
    }

    override fun setLayerLevel(layer: Int, level: Float, quantum: Quantum, fadeBeats: Float) {
        levels[layer] = level
        music?.setLayerLevel(layer, level, quantum, fadeBeats)
    }

    override fun setMuffle(amount: Float) {
        muffle = amount
        music?.setMuffle(amount)
    }

    override fun setVolume(volume: Float) {
        this.volume = volume
        music?.setVolume(volume)
    }

    /** Disposes of the music if it has arrived, and of any that arrives later. */
    override fun dispose() {
        if (disposed) return
        disposed = true
        playing = false
        // Let go of it too: a stage's decoded stems are megabytes.
        music?.dispose()
        music = null
    }
}

/** A single track still on its way; [PendingLayeredMusic] for a [Music]. */
class PendingMusic : Music {
    private var music: Music? = null
    private var volume = 1f
    private var looping = false
    private var playing = false
    private var disposed = false

    override val isPlaying: Boolean get() = music?.isPlaying ?: playing

    override var isLooping: Boolean
        get() = looping
        set(value) {
            looping = value
            music?.isLooping = value
        }

    /** As [PendingLayeredMusic.arrive]. */
    fun arrive(arrived: Music) {
        if (disposed || music != null) {
            arrived.dispose()
            return
        }
        arrived.isLooping = looping
        arrived.setVolume(volume)
        if (playing) arrived.play()
        music = arrived
    }

    override fun play() {
        if (disposed) return
        playing = true
        music?.play()
    }

    override fun pause() {
        playing = false
        music?.pause()
    }

    override fun setVolume(volume: Float) {
        this.volume = volume
        music?.setVolume(volume)
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        playing = false
        music?.dispose()
        music = null
    }
}
