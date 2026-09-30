package at.smiech.engine.impl

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import at.smiech.engine.LayeredMusic
import at.smiech.engine.Quantum
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * [LayeredMusic] on a streaming [AudioTrack]: a thread at audio priority pulls frames from the
 * [StemMixer] and writes them to the track.
 *
 * Structured like the desktop's `DesktopLayeredMusic`, for the same reasons: one thread for the
 * music's whole life, and a pause that fades out and lets the track play what it has before
 * stopping, rather than cutting a waveform off mid-cycle.
 */
class AndroidLayeredMusic(
    private val mixer: StemMixer,
    /**
     * Told once, on [dispose], so whatever handed this out can let go of it: a run that flies on
     * to the next stage opens that stage's music and disposes of this one, stems and all.
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
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val track = try {
            openTrack()
        } catch (exc: Exception) {
            Log.w(TAG, "Layered music unavailable", exc)
            return
        }
        val pcm = ShortArray(WRITE_FRAMES * 2)
        var sounding = false
        try {
            while (!disposed) {
                when {
                    wantPlaying -> {
                        if (!sounding) {
                            mixer.fadeTransport(on = true)
                            track.play()
                            sounding = true
                        }
                        write(track, pcm)
                        // Music that does not loop stops itself at its end, as if paused there.
                        if (mixer.hasEnded) wantPlaying = false
                    }

                    sounding -> {
                        mixer.fadeTransport(on = false)
                        while (!mixer.isSilenced) write(track, pcm)
                        // In streaming mode, stop() plays out what was written and then stops.
                        track.stop()
                        sounding = false
                    }

                    else -> lock.withLock {
                        if (!wantPlaying && !disposed) wake.await(IDLE_WAIT_MILLIS, TimeUnit.MILLISECONDS)
                    }
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (exc: Exception) {
            Log.w(TAG, "Layered music stopped", exc)
        } finally {
            track.release()
        }
    }

    private fun openTrack(): AudioTrack {
        val rate = mixer.sampleRate
        val minimum = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minimum, BUFFER_FRAMES * BYTES_PER_FRAME))
            .build()
    }

    private fun write(track: AudioTrack, pcm: ShortArray) {
        mixer.render(pcm, WRITE_FRAMES)
        var written = 0
        while (written < pcm.size && !disposed) {
            val result = track.write(pcm, written, pcm.size - written)
            if (result < 0) error("AudioTrack write failed: $result")
            written += result
        }
    }

    private companion object {
        const val TAG = "CyanBat"
        const val BYTES_PER_FRAME = 4

        /** Written at a time: about 23 ms at 22.05 kHz. */
        const val WRITE_FRAMES = 512

        /** See the desktop's; the same trade of latency against starving, about 90 ms. */
        const val BUFFER_FRAMES = 2048

        const val IDLE_WAIT_MILLIS = 250L
    }
}
