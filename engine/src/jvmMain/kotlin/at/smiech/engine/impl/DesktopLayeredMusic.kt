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
        val pcm = ShortArray(WRITE_FRAMES * 2)
        val bytes = ByteArray(WRITE_FRAMES * BYTES_PER_FRAME)
        var line: SourceDataLine? = null
        var sounding = false
        // When to ask for a line again after failing to get one; 0 to ask straight away.
        var retryAt = 0L
        // Whether the music has gone without a line since it last had one, so an outage is
        // reported once rather than every retry.
        var lost = false
        try {
            while (!disposed) {
                val open = line
                when {
                    wantPlaying && open == null -> {
                        if (System.nanoTime() >= retryAt) {
                            line = openLine(lost)
                            lost = line == null
                            if (line == null) {
                                retryAt = System.nanoTime() +
                                        TimeUnit.MILLISECONDS.toNanos(RETRY_MILLIS)
                            }
                        }
                        if (line == null) idle(whilePlaying = true)
                    }

                    wantPlaying && open != null -> {
                        if (!sounding) {
                            mixer.fadeTransport(on = true)
                            open.start()
                            sounding = true
                        }
                        if (write(open, pcm, bytes)) {
                            // Music that does not loop stops itself at its end, as if paused there.
                            if (mixer.hasEnded) wantPlaying = false
                        } else {
                            // The device went away under the line, or stopped taking what was
                            // written to it. A new line goes to whatever plays sound now; the
                            // music picks up there from where it had got to.
                            System.err.println("CyanBat: music lost its sound device, reopening")
                            open.close()
                            line = null
                            sounding = false
                            lost = true
                            SoundDevices.lineFailed()
                        }
                    }

                    sounding && open != null -> {
                        mixer.fadeTransport(on = false)
                        var delivered = true
                        while (delivered && !mixer.isSilenced) delivered = write(open, pcm, bytes)
                        if (delivered) drain(open)
                        open.stop()
                        sounding = false
                    }

                    else -> idle(whilePlaying = false)
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (exc: Exception) {
            System.err.println("CyanBat: layered music stopped - $exc")
        } finally {
            line?.close()
        }
    }

    /**
     * Sleeps until there is something to do, or [IDLE_WAIT_MILLIS] at most: while the music is
     * paused, until it plays, and while it waits for a line, until it is paused or disposed.
     */
    private fun idle(whilePlaying: Boolean) = lock.withLock {
        if (wantPlaying == whilePlaying && !disposed) {
            wake.await(IDLE_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * A line on the system's output, open and stopped, or null when none can be had: no device, or
     * none that takes the music's format, which is also what a device that has just arrived can
     * look like for a moment.
     *
     * @param retrying whether this follows a failure already reported.
     */
    private fun openLine(retrying: Boolean): SourceDataLine? {
        val format = AudioFormat(mixer.sampleRate.toFloat(), 16, 2, true, false)
        var line: SourceDataLine? = null
        return try {
            line = AudioSystem.getSourceDataLine(format)
            line.open(format, BUFFER_FRAMES * BYTES_PER_FRAME)
            if (retrying) System.err.println("CyanBat: layered music back on a sound device")
            line
        } catch (exc: Exception) {
            line?.close()
            // The game plays on in silence, and asks again in a moment.
            if (!retrying) System.err.println("CyanBat: layered music unavailable - $exc")
            SoundDevices.lineFailed()
            null
        }
    }

    /**
     * Mixes the next frames and hands them to [line], waiting for room rather than blocking in the
     * line's own write, which on a device that has gone away can wait forever.
     *
     * @return false when the line takes nothing for [STALL_MILLIS], or fails outright.
     */
    private fun write(line: SourceDataLine, pcm: ShortArray, bytes: ByteArray): Boolean {
        mixer.render(pcm, WRITE_FRAMES)
        for (i in pcm.indices) {
            val sample = pcm[i].toInt()
            bytes[2 * i] = sample.toByte()
            bytes[2 * i + 1] = (sample shr 8).toByte()
        }
        return try {
            var offset = 0
            var progressAt = System.nanoTime()
            while (offset < bytes.size) {
                val available = line.available()
                val room = available - available % BYTES_PER_FRAME
                if (room > 0) {
                    offset += line.write(bytes, offset, minOf(room, bytes.size - offset))
                    progressAt = System.nanoTime()
                } else if (stalledSince(progressAt)) {
                    return false
                } else {
                    Thread.sleep(WAIT_FOR_ROOM_MILLIS)
                }
            }
            true
        } catch (exc: InterruptedException) {
            throw exc
        } catch (_: Exception) {
            false
        }
    }

    /** Lets what is in [line] play out, as [SourceDataLine.drain] would, but not forever. */
    private fun drain(line: SourceDataLine) {
        val since = System.nanoTime()
        while (line.available() < line.bufferSize && !stalledSince(since)) {
            Thread.sleep(WAIT_FOR_ROOM_MILLIS)
        }
    }

    private fun stalledSince(nanos: Long) =
        System.nanoTime() - nanos > TimeUnit.MILLISECONDS.toNanos(STALL_MILLIS)

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

        /** How long the music goes without a line before it asks for one again. */
        const val RETRY_MILLIS = 1_000L

        /** How long a line may take nothing before it is taken for lost: many times its buffer. */
        const val STALL_MILLIS = 1_000L

        /** How long to wait for a line to make room: a fraction of a write's length. */
        const val WAIT_FOR_ROOM_MILLIS = 4L
    }
}
