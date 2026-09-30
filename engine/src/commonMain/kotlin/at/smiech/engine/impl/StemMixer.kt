package at.smiech.engine.impl

import at.smiech.engine.MusicGrid
import at.smiech.engine.Quantum
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToLong
import kotlin.math.tan
import kotlin.math.tanh

/**
 * The part of a [at.smiech.engine.LayeredMusic] that every platform shares: decoding the stems,
 * mixing them at their levels, and landing each change of level on the grid. A platform wraps it
 * in a thread that pulls frames out of [render] and writes them to a device.
 *
 * Two threads touch it. The game thread only ever calls the setters, which leave orders behind in
 * fields the audio thread reads once a chunk; everything else belongs to the audio thread. An
 * order is a whole immutable object swapped in at once, so the audio thread sees either all of it
 * or none of it and nothing needs a lock - which common code could not take anyway.
 *
 * Every stem is decoded on every chunk, heard or not, because a stem's place in the music is how
 * far it has been read: one skipped while it was silent would come back out of step.
 *
 * Music loops unless it is told not to; see [isLooping]. A piece of one stem played once - the game
 * over's - goes through here too, so that every piece of music the game plays is decoded the same
 * way on every platform.
 */
class StemMixer(stems: List<ImaAdpcmClip>, grid: MusicGrid) {
    init {
        require(stems.isNotEmpty()) { "Layered music needs at least one stem" }
        require(stems.all { it.sampleRate == stems[0].sampleRate }) {
            "Every stem has to share one sample rate"
        }
    }

    val sampleRate: Int = stems[0].sampleRate
    val layerCount: Int = stems.size

    private val framesPerBeat = grid.framesPerBeat(sampleRate)
    private val framesPerBar = grid.framesPerBar(sampleRate)
    private val cursors = stems.map { it.cursor() }
    private val layers = Array(layerCount) { Layer() }

    /** Once through the music: its longest stem, which every shorter one divides. */
    private val length = stems.maxOf { it.frames }.toLong()

    /**
     * Game thread. Whether the music goes round again at its end, as layered music always does, or
     * stops there, as a track the game plays once does; see [hasEnded]. Turned off while the music
     * is going round, it stops at the end of the time through that it is in.
     */
    @Volatile
    var isLooping = true

    // Written by the game thread, read by the audio thread.
    @Volatile
    private var orders: Array<Order?> = arrayOfNulls(layerCount)

    @Volatile
    private var muffleTarget = 0f

    @Volatile
    private var volumeTarget = 1f

    /** Frames rendered since the top. The grid is counted from here, as the stems are. */
    var position = 0L
        private set

    /**
     * Audio thread. True once music that does not loop has played to its end. From there it renders
     * silence, and the next [fadeTransport] on starts it again from the top.
     */
    var hasEnded = false
        private set

    private var muffle = 0f
    private var volume = 1f
    private var transport = 1f
    private var transportTarget = 1f

    // The low-pass filter's state, per channel.
    private var leftState1 = 0f
    private var leftState2 = 0f
    private var rightState1 = 0f
    private var rightState2 = 0f

    private val mix = FloatArray(CHUNK_FRAMES * 2)
    private val stem = FloatArray(CHUNK_FRAMES * 2)

    private val openHz = minOf(OPEN_HZ, OPEN_FRACTION_OF_RATE * sampleRate)
    private val smoothing = 1f - exp(-CHUNK_FRAMES / (GLIDE_SECONDS * sampleRate)).toFloat()
    private val transportStep = 1f / (sampleRate * DECLICK_SECONDS)

    /** Game thread. See [at.smiech.engine.LayeredMusic.setLayerLevel]. */
    fun setLayerLevel(layer: Int, level: Float, quantum: Quantum, fadeBeats: Float) {
        require(layer in 0 until layerCount) { "No layer $layer in music of $layerCount" }
        val next = orders.copyOf()
        next[layer] = Order(level.coerceIn(0f, 1f), quantum, fadeBeats.coerceAtLeast(0f))
        orders = next
    }

    /** Game thread. See [at.smiech.engine.LayeredMusic.setMuffle]. */
    fun setMuffle(amount: Float) {
        muffleTarget = amount.coerceIn(0f, 1f)
    }

    /** Game thread. See [at.smiech.engine.LayeredMusic.setVolume]. */
    fun setVolume(volume: Float) {
        volumeTarget = volume.coerceIn(0f, 1f)
    }

    /**
     * Audio thread. Fades the output to silence, or back up from it, over a few milliseconds, so a
     * pause does not cut a waveform off halfway and click. The music's own position runs on
     * through the fade, and a platform pauses its device once [isSilenced] says it can.
     *
     * Music that has played to its end starts again from the top when it is faded back on.
     */
    fun fadeTransport(on: Boolean) {
        if (on && hasEnded) rewind()
        transportTarget = if (on) 1f else 0f
    }

    /** Audio thread. True once a fade out asked for by [fadeTransport] has finished. */
    val isSilenced: Boolean get() = transportTarget == 0f && transport == 0f

    /** Audio thread. Fills [out] with the next [frames] frames, interleaved 16-bit stereo. */
    fun render(out: ShortArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val count = minOf(CHUNK_FRAMES, frames - done)
            renderChunk(out, done, count)
            done += count
        }
    }

    private fun renderChunk(out: ShortArray, offset: Int, count: Int) {
        takeOrders()

        // Music that does not loop plays up to the end of the time through it is in; whatever is
        // left of the chunk after that, and every chunk after it, is silence.
        val audible = when {
            hasEnded -> 0
            isLooping -> count
            else -> {
                val untilEnd = length - position % length
                if (untilEnd <= count) hasEnded = true
                minOf(untilEnd, count.toLong()).toInt()
            }
        }

        mix.fill(0f, 0, count * 2)
        for (index in layers.indices) {
            if (audible == 0) break
            cursors[index].read(stem, 0, audible)
            val layer = layers[index]
            if (layer.isSilentThrough(position, position + audible)) continue
            for (f in 0 until audible) {
                val gain = layer.gainAt(position + f)
                mix[2 * f] += stem[2 * f] * gain
                mix[2 * f + 1] += stem[2 * f + 1] * gain
            }
            layer.settle(position + audible)
        }

        // Glided once a chunk and interpolated across it, so a caller setting these once a frame
        // gets a smooth curve rather than a staircase of steps a chunk wide.
        val volumeFrom = volume
        volume += (volumeTarget - volume) * smoothing
        val wetFrom = wetFor(muffle)
        muffle += (muffleTarget - muffle) * smoothing
        val wetTo = wetFor(muffle)
        val filter = Svf(cutoffFor(muffle), sampleRate)

        for (f in 0 until count) {
            val t = (f + 1).toFloat() / count
            val gain = volumeFrom + (volume - volumeFrom) * t
            val wet = wetFrom + (wetTo - wetFrom) * t
            transport = when {
                transport < transportTarget -> minOf(transportTarget, transport + transportStep)
                transport > transportTarget -> max(transportTarget, transport - transportStep)
                else -> transport
            }

            val left = mix[2 * f]
            val right = mix[2 * f + 1]

            var v3 = left - leftState2
            var v1 = filter.a1 * leftState1 + filter.a2 * v3
            var v2 = leftState2 + filter.a2 * leftState1 + filter.a3 * v3
            leftState1 = 2f * v1 - leftState1
            leftState2 = 2f * v2 - leftState2
            val lowLeft = v2

            v3 = right - rightState2
            v1 = filter.a1 * rightState1 + filter.a2 * v3
            v2 = rightState2 + filter.a2 * rightState1 + filter.a3 * v3
            rightState1 = 2f * v1 - rightState1
            rightState2 = 2f * v2 - rightState2
            val lowRight = v2

            val level = gain * transport
            out[2 * (offset + f)] = toPcm((left + (lowLeft - left) * wet) * level)
            out[2 * (offset + f) + 1] = toPcm((right + (lowRight - right) * wet) * level)
        }
        flushDenormals()
        position += count
    }

    /** Picks up whatever the game thread has ordered since the last chunk. */
    private fun takeOrders() {
        val current = orders
        for (index in layers.indices) {
            val order = current[index] ?: continue
            val layer = layers[index]
            if (order === layer.seen) continue
            layer.seen = order
            schedule(layer, order)
        }
    }

    /**
     * Takes every stem back to its first frame, and the grid back with them. Each layer holds the
     * level it had, and its latest order is put on the grid again, counted from the new top.
     */
    private fun rewind() {
        for (index in layers.indices) {
            val layer = layers[index]
            val level = layer.gainAt(position)
            layer.current = Ramp(0L, 0L, level, level)
            layer.pending = null
            layer.seen = null
            cursors[index].rewind()
        }
        position = 0L
        hasEnded = false
    }

    /**
     * Puts [order] on the grid. The latest order for a layer replaces one still waiting for its
     * boundary: the layer carries on as it was until the new one lands, and the new one fades from
     * wherever that leaves it.
     */
    private fun schedule(layer: Layer, order: Order) {
        val now = position
        val fade = max(MIN_FADE_FRAMES, (order.fadeBeats * framesPerBeat).roundToLong())
        layer.pending = null
        val rising = order.level > layer.current.to
        val unit = if (order.quantum == Quantum.BAR) framesPerBar else framesPerBeat

        val start: Long
        val end: Long
        when {
            order.quantum == Quantum.IMMEDIATE -> {
                start = now
                end = now + fade
            }
            // A layer coming in has to be all the way up on the beat, which is where its first
            // note lands, so its fade runs in the moment before.
            rising -> {
                end = nextBoundary(now + fade, unit)
                start = end - fade
            }
            // One going out plays the beat it leaves on and fades after it.
            else -> {
                start = nextBoundary(now, unit)
                end = start + fade
            }
        }

        val ramp = Ramp(start, end, layer.current.at(start), order.level)
        if (start <= now) layer.current = ramp else layer.pending = ramp
    }

    /** The first grid line at or after [atLeast], lines falling every [unit] frames from the top. */
    private fun nextBoundary(atLeast: Long, unit: Double): Long {
        val k = ceil(atLeast / unit)
        val frame = round(k * unit).toLong()
        return if (frame >= atLeast) frame else round((k + 1) * unit).toLong()
    }

    /**
     * The filter opens exponentially as the muffle lifts, which is how a sweep has to move to sound
     * even. At no muffle it is out of the signal altogether, not merely wide open: the crossfade to
     * it is done while its cutoff is still high enough that the switch cannot be heard.
     */
    private fun cutoffFor(muffle: Float): Float = openHz * (FLOOR_HZ / openHz).pow(muffle)

    private fun wetFor(muffle: Float): Float = (muffle * WET_PER_MUFFLE).coerceAtMost(1f)

    /** Very small floats are slow on some processors, and a filter in silence decays toward them. */
    private fun flushDenormals() {
        if (abs(leftState1) < DENORMAL) leftState1 = 0f
        if (abs(leftState2) < DENORMAL) leftState2 = 0f
        if (abs(rightState1) < DENORMAL) rightState1 = 0f
        if (abs(rightState2) < DENORMAL) rightState2 = 0f
    }

    private fun toPcm(sample: Float): Short = (softClip(sample) * 32767f).toInt().toShort()

    /**
     * Straight through below [CLIP_KNEE], rounded off above it. The stems are mixed to leave
     * headroom with every layer up, so this should only ever catch a rare coinciding peak - and a
     * rounded peak is far kinder than a wrapped one.
     */
    private fun softClip(x: Float): Float {
        val magnitude = abs(x)
        if (magnitude <= CLIP_KNEE) return x
        val over = (magnitude - CLIP_KNEE) / (1f - CLIP_KNEE)
        val clipped = CLIP_KNEE + (1f - CLIP_KNEE) * tanh(over)
        return if (x < 0f) -clipped else clipped
    }

    /** What the game thread last asked of one layer. */
    private class Order(val level: Float, val quantum: Quantum, val fadeBeats: Float)

    /** A straight line from [from] at [start] to [to] at [end], held flat either side. */
    private class Ramp(val start: Long, val end: Long, val from: Float, val to: Float) {
        fun at(frame: Long): Float = when {
            frame <= start -> from
            frame >= end -> to
            else -> from + (to - from) * ((frame - start).toFloat() / (end - start))
        }
    }

    private class Layer {
        var current = Ramp(0L, 0L, 0f, 0f)

        /** A ramp waiting for its grid line, which takes over from [current] once reached. */
        var pending: Ramp? = null

        var seen: Order? = null

        fun gainAt(frame: Long): Float {
            val next = pending
            return if (next != null && frame >= next.start) next.at(frame) else current.at(frame)
        }

        /** Hands over to the pending ramp once [frame] has passed its start. */
        fun settle(frame: Long) {
            val next = pending ?: return
            if (frame >= next.start) {
                current = next
                pending = null
            }
        }

        fun isSilentThrough(from: Long, until: Long): Boolean {
            val next = pending
            if (next != null && next.start < until) return false
            return current.to == 0f && current.end <= from
        }
    }

    /**
     * Coefficients of a two-pole state variable low-pass, in the topology-preserving form, which
     * stays stable while its cutoff is being swept - the whole point of it here.
     */
    private class Svf(cutoffHz: Float, sampleRate: Int) {
        val a1: Float
        val a2: Float
        val a3: Float

        init {
            val g = tan(PI * cutoffHz / sampleRate).toFloat()
            a1 = 1f / (1f + g * (g + DAMPING))
            a2 = g * a1
            a3 = g * a2
        }
    }

    private companion object {
        /** Frames mixed at a time: small enough that a glide is smooth, large enough to be cheap. */
        const val CHUNK_FRAMES = 256

        /** How long [setMuffle] and [setVolume] take to get most of the way to a new value. */
        const val GLIDE_SECONDS = 0.03f

        /** A pause fades out over this rather than cutting mid-waveform. */
        const val DECLICK_SECONDS = 0.01f

        /** The shortest fade a layer is given, however short it is asked to be: 2 ms, no click. */
        const val MIN_FADE_FRAMES = 48L

        /** The muffle filter's cutoff with no muffle, before it is taken out of the signal. */
        const val OPEN_HZ = 9000f
        const val OPEN_FRACTION_OF_RATE = 0.45f

        /** Where a full muffle closes the filter down to: the bass and the thump of the drums. */
        const val FLOOR_HZ = 250f

        /** How fast the filter is crossfaded in as the muffle rises from nothing. */
        const val WET_PER_MUFFLE = 6f

        /** 1/Q. A little under the flat 1.41, for a hint of resonance as the filter sweeps. */
        const val DAMPING = 1.1f

        const val CLIP_KNEE = 0.9f
        const val DENORMAL = 1e-15f
    }
}
