package at.smiech.engine.impl

import at.smiech.engine.LayeredMusic
import at.smiech.engine.Quantum
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.Volatile

/**
 * [LayeredMusic] on the app's audio engine: a player node of its own, kept fed by a serial dispatch
 * queue that pulls chunks from the [StemMixer] and schedules them back to back, a few ahead. Each
 * chunk handed back by the player is rendered again and put at the end of the line, so the queue is
 * the desktop's pump thread, woken by the player rather than blocked on a line.
 *
 * The queue is the mixer's audio thread: everything but the game thread's setters runs on it, one
 * block at a time. A pause fades the mixer out, plays out what is queued and stops the player; the
 * next [play] fades it back in where it left off.
 */
@OptIn(ExperimentalForeignApi::class)
class IosLayeredMusic(
    private val mixer: StemMixer,
    private val device: IosAudioDevice,
    /**
     * Called once, on [dispose], so whatever created this can drop it: the next stage's run opens
     * its own music and disposes of this one.
     */
    private val onDisposed: (LayeredMusic) -> Unit = {},
) : LayeredMusic {
    private val queue = dispatch_queue_create("at.smiech.cyanbat.music", null)
    private val format = AVAudioFormat(
        standardFormatWithSampleRate = mixer.sampleRate.toDouble(),
        channels = 2u,
    )
    private val player = AVAudioPlayerNode()

    // The queue's own.
    private val pcm = ShortArray(CHUNK_FRAMES * 2)
    private val free = ArrayDeque<AVAudioPCMBuffer>()
    private var inFlight = 0
    private var sounding = false

    @Volatile
    private var wantPlaying = false

    @Volatile
    private var disposed = false

    init {
        repeat(CHUNKS) {
            free.addLast(AVAudioPCMBuffer(pCMFormat = format, frameCapacity = CHUNK_FRAMES.toUInt()))
        }
        device.attach(this, player, format)
    }

    override val layerCount: Int get() = mixer.layerCount
    override val isPlaying: Boolean get() = wantPlaying && !disposed

    override fun play() {
        if (disposed) return
        wantPlaying = true
        dispatch_async(queue) { pump() }
    }

    override fun pause() {
        wantPlaying = false
        dispatch_async(queue) { pump() }
    }

    override fun setLayerLevel(layer: Int, level: Float, quantum: Quantum, fadeBeats: Float) =
        mixer.setLayerLevel(layer, level, quantum, fadeBeats)

    override fun setMuffle(amount: Float) = mixer.setMuffle(amount)

    override fun setVolume(volume: Float) = mixer.setVolume(volume)

    override fun dispose() {
        if (disposed) return
        disposed = true
        wantPlaying = false
        dispatch_async(queue) {
            player.stop()
            // The engine's graph is the main thread's.
            dispatch_async(dispatch_get_main_queue()) { device.detach(this, player) }
        }
        onDisposed(this)
    }

    /**
     * Sets the music going again on an engine that was stopped under it, if it was sounding:
     * stopping the player hands back whatever was still queued, and the pump starts it afresh.
     */
    internal fun restart() {
        dispatch_async(queue) {
            if (disposed || !sounding) return@dispatch_async
            player.stop()
            sounding = false
            pump()
        }
    }

    /**
     * Keeps the player fed while the music should play, and once it should not, fades it out,
     * plays that out and stops. Runs on the queue, whenever something changed: a play or a pause,
     * or a chunk coming back.
     */
    private fun pump() {
        if (disposed) return
        when {
            wantPlaying -> {
                if (!sounding) {
                    // No engine, no music: the game plays on in silence until a restart.
                    if (!device.ensureRunning()) return
                    mixer.fadeTransport(on = true)
                    player.play()
                    sounding = true
                }
                while (wantPlaying && free.isNotEmpty()) {
                    schedule(free.removeFirst())
                    // Music that does not loop stops itself at its end, as if paused there.
                    if (mixer.hasEnded) wantPlaying = false
                }
            }

            sounding -> {
                mixer.fadeTransport(on = false)
                while (!mixer.isSilenced && free.isNotEmpty()) schedule(free.removeFirst())
                if (mixer.isSilenced && inFlight == 0) {
                    player.stop()
                    sounding = false
                }
            }
        }
    }

    /** Renders the next chunk into [buffer] and queues it on the player. */
    private fun schedule(buffer: AVAudioPCMBuffer) {
        mixer.render(pcm, CHUNK_FRAMES)
        val channels = buffer.floatChannelData ?: return
        val left = channels[0] ?: return
        val right = channels[1] ?: return
        for (frame in 0 until CHUNK_FRAMES) {
            left[frame] = pcm[2 * frame] / PCM_SCALE
            right[frame] = pcm[2 * frame + 1] / PCM_SCALE
        }
        buffer.frameLength = CHUNK_FRAMES.toUInt()
        inFlight++
        player.scheduleBuffer(buffer) {
            dispatch_async(queue) {
                inFlight--
                free.addLast(buffer)
                pump()
            }
        }
    }

    private companion object {
        /** Frames in a chunk: about 23 ms at 22.05 kHz, as the desktop writes at a time. */
        const val CHUNK_FRAMES = 512

        /**
         * Chunks queued at once, about 140 ms. A little more than the desktop's line holds, since
         * a chunk comes back through a dispatch to the queue rather than a blocked write; short,
         * because a muffle or a layer responding to the player waits behind everything queued.
         */
        const val CHUNKS = 6

        /** From 16-bit samples to the engine's floats. */
        const val PCM_SCALE = 32768f
    }
}
