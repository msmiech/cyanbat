package at.smiech.cyanbat.scenery

import kotlin.math.abs
import kotlin.math.floor

/**
 * Deterministic noise for the endless stage's scenery: the same seed and coordinate always give
 * the same value, on every platform, so a land is the same wherever it is cut into chunks and
 * whenever it is made.
 *
 * Integer hashing rather than a random generator, because a chunk asks for the land at arbitrary
 * coordinates in any order: a column of a mountain range must come out the same whether it is the
 * first column of one chunk or the last of the one before.
 */
internal object Noise {

    /** A well-mixed 32-bit hash of [x] and [seed]. */
    fun hash(x: Int, seed: Int): Int {
        var h = x * -0x61c88647 + seed * 0x27d4eb2d
        h = (h xor (h ushr 15)) * -0x7a143595
        h = (h xor (h ushr 13)) * -0x3d4d51cb
        return h xor (h ushr 16)
    }

    /** A hash of two coordinates. */
    fun hash(x: Int, y: Int, seed: Int): Int = hash(x, hash(y, seed))

    /** [hash] as 0..1. */
    fun unit(x: Int, seed: Int): Float = (hash(x, seed) ushr 8) / 16777216f

    /** [hash] of two coordinates as 0..1. */
    fun unit(x: Int, y: Int, seed: Int): Float = (hash(x, y, seed) ushr 8) / 16777216f

    /** Smooth one-dimensional value noise, 0..1, with a feature every unit of [x]. */
    fun value(x: Float, seed: Int): Float {
        val cell = floor(x)
        val i = cell.toInt()
        val t = x - cell
        val s = t * t * (3f - 2f * t)
        val a = unit(i, seed)
        val b = unit(i + 1, seed)
        return a + (b - a) * s
    }

    /**
     * Fractal noise, 0..1: [octaves] of [value] noise, each twice as fine and half as strong as the
     * one before, for rolling ground with detail on its detail.
     */
    fun fbm(x: Float, seed: Int, octaves: Int = 4, gain: Float = 0.5f): Float {
        var sum = 0f
        var amplitude = 1f
        var total = 0f
        var frequency = 1f
        for (octave in 0 until octaves) {
            sum += value(x * frequency, seed + octave * 101) * amplitude
            total += amplitude
            amplitude *= gain
            frequency *= 2f
        }
        return sum / total
    }

    /**
     * Ridged fractal noise, 0..1: sharp crests and soft valleys, for mountain ranges. Each octave
     * folds its noise about its middle, so where it crosses the middle there is a peak.
     */
    fun ridged(x: Float, seed: Int, octaves: Int = 4, gain: Float = 0.5f): Float {
        var sum = 0f
        var amplitude = 1f
        var total = 0f
        var frequency = 1f
        for (octave in 0 until octaves) {
            val n = 1f - abs(value(x * frequency, seed + octave * 131) * 2f - 1f)
            sum += n * n * amplitude
            total += amplitude
            amplitude *= gain
            frequency *= 2f
        }
        return sum / total
    }
}
