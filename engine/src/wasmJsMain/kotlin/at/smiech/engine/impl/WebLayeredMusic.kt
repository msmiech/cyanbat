package at.smiech.engine.impl

import at.smiech.engine.LayeredMusic
import at.smiech.engine.Quantum
import kotlinx.browser.window
import org.khronos.webgl.toFloat32Array

/**
 * [LayeredMusic] in a browser: a timer pulls chunks from the [StemMixer] and schedules each on the
 * context's clock where the last one ends, keeping [LOOKAHEAD_SECONDS] ahead of it, as the
 * desktop's thread keeps a line's buffer full. The browser plays the chunks back to back, to the
 * sample, so the music has no seams however late the timer fires, as long as it fires within the
 * lookahead. That is also the music's latency: a muffle or a layer answering the player waits behind
 * what is already scheduled.
 *
 * A pause fades the mixer out and lets what is scheduled play out, as on the other platforms.
 *
 * @param output where the music plays: the [WebAudio]'s own node.
 */
internal class WebLayeredMusic(
    private val context: AudioContext,
    output: AudioNode,
    private val mixer: StemMixer,
) : LayeredMusic {
    // A node of its own, which dispose disconnects to cut off what is already scheduled.
    private val out = context.createGain().apply { connect(output) }
    private val pcm = ShortArray(CHUNK_FRAMES * 2)
    private val left = FloatArray(CHUNK_FRAMES)
    private val right = FloatArray(CHUNK_FRAMES)
    private val chunkSeconds = CHUNK_FRAMES.toDouble() / mixer.sampleRate

    private var wantPlaying = false
    private var sounding = false
    private var disposed = false
    private var timer: Int? = null

    /** Where on the context's clock the next chunk starts. */
    private var nextStart = 0.0

    override val layerCount: Int get() = mixer.layerCount
    override val isPlaying: Boolean get() = wantPlaying && !disposed

    override fun play() {
        if (disposed) return
        wantPlaying = true
        if (timer == null) {
            timer = window.setInterval({
                pump()
                null
            }, PUMP_MILLIS)
        }
        pump()
    }

    override fun pause() {
        wantPlaying = false
    }

    override fun setLayerLevel(layer: Int, level: Float, quantum: Quantum, fadeBeats: Float) =
        mixer.setLayerLevel(layer, level, quantum, fadeBeats)

    override fun setMuffle(amount: Float) = mixer.setMuffle(amount)

    override fun setVolume(volume: Float) = mixer.setVolume(volume)

    override fun dispose() {
        if (disposed) return
        disposed = true
        wantPlaying = false
        stopPump()
        out.disconnect()
    }

    /** Schedules chunks until the music is [LOOKAHEAD_SECONDS] ahead of the clock, or has stopped. */
    private fun pump() {
        try {
            val now = context.currentTime
            // Run dry, because the page was busy for longer than the lookahead, or starting after a
            // pause: the music goes on from a moment from now, rather than from a time already gone.
            if (nextStart < now + MIN_LEAD_SECONDS) nextStart = now + MIN_LEAD_SECONDS
            while (nextStart < now + LOOKAHEAD_SECONDS) {
                when {
                    wantPlaying -> {
                        if (!sounding) {
                            mixer.fadeTransport(on = true)
                            sounding = true
                        }
                        schedule()
                        // Music that does not loop stops itself at its end, as if paused there.
                        if (mixer.hasEnded) wantPlaying = false
                    }

                    sounding -> {
                        mixer.fadeTransport(on = false)
                        while (!mixer.isSilenced) schedule()
                        sounding = false
                    }

                    else -> {
                        stopPump()
                        return
                    }
                }
            }
        } catch (exc: Exception) {
            println("CyanBat: layered music stopped - $exc")
            stopPump()
        }
    }

    private fun schedule() {
        mixer.render(pcm, CHUNK_FRAMES)
        for (frame in 0 until CHUNK_FRAMES) {
            left[frame] = pcm[2 * frame] / FULL_SCALE
            right[frame] = pcm[2 * frame + 1] / FULL_SCALE
        }
        val chunk = context.createBuffer(2, CHUNK_FRAMES, mixer.sampleRate.toFloat())
        chunk.copyToChannel(left.toFloat32Array(), 0)
        chunk.copyToChannel(right.toFloat32Array(), 1)
        val source = context.createBufferSource()
        source.buffer = chunk
        source.connect(out)
        source.start(nextStart)
        nextStart += chunkSeconds
    }

    private fun stopPump() {
        timer?.let(window::clearInterval)
        timer = null
    }

    private companion object {
        /** Frames scheduled at a time: about 23 ms at 22.05 kHz, as the desktop writes them. */
        const val CHUNK_FRAMES = 512

        /**
         * How far ahead of the clock the music is kept, about the desktop line's buffer plus a
         * chunk. Long enough that a busy frame does not run it dry, short enough that the music
         * answers the player promptly.
         */
        const val LOOKAHEAD_SECONDS = 0.12

        /** How soon after now a chunk can be counted on to start, after the music ran dry. */
        const val MIN_LEAD_SECONDS = 0.02

        /** How often the timer tops the schedule up; a hidden page's timer fires far less often. */
        const val PUMP_MILLIS = 20

        /** A 16-bit sample's full scale, which Web Audio's samples are fractions of. */
        const val FULL_SCALE = 32768f
    }
}
