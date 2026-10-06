package at.smiech.engine.impl

import at.smiech.engine.LayeredMusic
import at.smiech.engine.Quantum
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.withLock

/**
 * [LayeredMusic] on a javax.sound line: a daemon thread, which never holds the JVM open, pulls
 * frames from the [StemMixer] and writes them out.
 *
 * One thread serves the music's whole life, started by the first [play]. A pause fades the mixer
 * out, drains the line and stops it; the thread then waits for the next [play] rather than exiting,
 * because a second thread started while the first was still blocked in a write would put two copies
 * of the music on the line.
 */
class DesktopLayeredMusic(
    private val mixer: StemMixer,
    /**
     * Called once, on [dispose], so whatever created this can drop it: the next stage's run opens
     * its own music and disposes of this one.
     */
    private val onDisposed: (LayeredMusic) -> Unit = {},
) : LayeredMusic {
    private val lock = ReentrantLock()
    private val wake = lock.newCondition()
    private var thread: Thread? = null

    @Volatile
    private var wantPlaying = false

    @Volatile
    private var disposed = false

    override val layerCount: Int get() = mixer.layerCount
    override val isPlaying: Boolean get() = wantPlaying && !disposed

    override fun play() {
        lock.withLock {
            if (disposed) return
            wantPlaying = true
            if (thread == null) {
                thread = Thread(::pump, "cyanbat-layered-music").apply {
                    isDaemon = true
                    start()
                }
            }
            wake.signalAll()
        }
    }

    override fun pause() {
        wantPlaying = false
    }

    override fun setLayerLevel(layer: Int, level: Float, quantum: Quantum, fadeBeats: Float) =
        mixer.setLayerLevel(layer, level, quantum, fadeBeats)

    override fun setMuffle(amount: Float) = mixer.setMuffle(amount)

    override fun setVolume(volume: Float) = mixer.setVolume(volume)

    override fun dispose() {
        lock.withLock {
            if (disposed) return
            disposed = true
            wantPlaying = false
            wake.signalAll()
        }
        onDisposed(this)
    }

    private fun pump() {
        val format = AudioFormat(mixer.sampleRate.toFloat(), 16, 2, true, false)
        val line = try {
            AudioSystem.getSourceDataLine(format)
                .apply { open(format, BUFFER_FRAMES * BYTES_PER_FRAME) }
        } catch (exc: Exception) {
            // No device, or none that takes this format: the game plays on in silence.
            System.err.println("CyanBat: layered music unavailable - $exc")
            return
        }
        val pcm = ShortArray(WRITE_FRAMES * 2)
        val bytes = ByteArray(WRITE_FRAMES * BYTES_PER_FRAME)
        var sounding = false
        try {
            while (!disposed) {
                when {
                    wantPlaying -> {
                        if (!sounding) {
                            mixer.fadeTransport(on = true)
                            line.start()
                            sounding = true
                        }
                        write(line, pcm, bytes)
                        // Music that does not loop stops itself at its end, as if paused there.
                        if (mixer.hasEnded) wantPlaying = false
                    }

                    sounding -> {
                        mixer.fadeTransport(on = false)
                        while (!mixer.isSilenced) write(line, pcm, bytes)
                        line.drain()
                        line.stop()
                        sounding = false
                    }

                    else -> lock.withLock {
                        if (!wantPlaying && !disposed) wake.await(
                            IDLE_WAIT_MILLIS,
                            TimeUnit.MILLISECONDS
                        )
                    }
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (exc: Exception) {
            System.err.println("CyanBat: layered music stopped - $exc")
        } finally {
            line.close()
        }
    }

    private fun write(line: SourceDataLine, pcm: ShortArray, bytes: ByteArray) {
        mixer.render(pcm, WRITE_FRAMES)
        for (i in pcm.indices) {
            val sample = pcm[i].toInt()
            bytes[2 * i] = sample.toByte()
            bytes[2 * i + 1] = (sample shr 8).toByte()
        }
        line.write(bytes, 0, bytes.size)
    }

    private companion object {
        const val BYTES_PER_FRAME = 4

        /** Frames written at a time: about 23 ms at 22.05 kHz. */
        const val WRITE_FRAMES = 512

        /**
         * The line's buffer, about 90 ms. Short, because a muffle or a layer
         * responding to the player waits behind everything already in it; long
         * enough that a busy frame does not starve it.
         */
        const val BUFFER_FRAMES = 2048

        /** How long a paused pump sleeps between checks, should a wake-up be missed. */
        const val IDLE_WAIT_MILLIS = 250L
    }
}
