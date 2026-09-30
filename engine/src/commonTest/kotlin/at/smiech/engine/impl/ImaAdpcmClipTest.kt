package at.smiech.engine.impl

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImaAdpcmClipTest {

    private fun sine(frames: Int, hz: Double, rate: Int, amplitude: Double = 0.5) =
        ShortArray(frames) { (sin(2 * PI * hz * it / rate) * amplitude * 32767).toInt().toShort() }

    private fun readAll(clip: ImaAdpcmClip, frames: Int): FloatArray =
        FloatArray(frames * 2).also { clip.cursor().read(it, 0, frames) }

    @Test
    fun `a tone survives the round trip`() {
        val rate = 22050
        val source = sine(rate, 440.0, rate)
        val clip = ImaAdpcmClip.parse(imaAdpcmWav(source, channels = 1, sampleRate = rate))
        assertEquals(rate, clip.frames)
        assertEquals(rate, clip.sampleRate)

        val decoded = readAll(clip, rate)
        var signal = 0.0
        var noise = 0.0
        for (i in source.indices) {
            val expected = source[i] / 32768.0
            signal += expected * expected
            val error = decoded[2 * i] - expected
            noise += error * error
        }
        val snr = 10 * log10(signal / noise)
        // Four bits a sample buys roughly what 8-bit PCM would, on a tone this plain.
        assertTrue(snr > 30.0, "round trip SNR was $snr dB")
    }

    /**
     * What layered music rests on: the frame after the last is the first, exactly, with nothing
     * decoded in between - even when the length is not a whole number of blocks.
     */
    @Test
    fun `the clip loops to its first frame without a gap`() {
        val rate = 8000
        val frames = 1234
        val clip = ImaAdpcmClip.parse(imaAdpcmWav(sine(frames, 330.0, rate), channels = 1, sampleRate = rate))

        val decoded = readAll(clip, frames * 3)
        for (i in 0 until frames) {
            assertEquals(decoded[2 * i], decoded[2 * (i + frames)], "frame $i, second time round")
            assertEquals(decoded[2 * i], decoded[2 * (i + 2 * frames)], "frame $i, third time round")
        }
    }

    @Test
    fun `stereo keeps its sides apart`() {
        val rate = 8000
        val frames = 800
        val left = sine(frames, 200.0, rate)
        val interleaved = ShortArray(frames * 2) { if (it % 2 == 0) left[it / 2] else 0 }
        val clip = ImaAdpcmClip.parse(imaAdpcmWav(interleaved, channels = 2, sampleRate = rate))

        val decoded = readAll(clip, frames)
        var leftEnergy = 0.0
        var rightEnergy = 0.0
        for (i in 0 until frames) {
            leftEnergy += decoded[2 * i] * decoded[2 * i]
            rightEnergy += decoded[2 * i + 1] * decoded[2 * i + 1]
        }
        assertTrue(sqrt(leftEnergy / frames) > 0.3, "the tone is on the left")
        assertTrue(sqrt(rightEnergy / frames) < 0.001, "and nothing is on the right")
    }

    @Test
    fun `a mono clip plays on both sides`() {
        val decoded = readAll(constantClip(8192, frames = 100, sampleRate = 8000), 100)
        for (i in 0 until 100) {
            assertEquals(0.25f, decoded[2 * i])
            assertEquals(0.25f, decoded[2 * i + 1])
        }
    }

    @Test
    fun `a clip of silence decodes to silence`() {
        val decoded = readAll(constantClip(0, frames = 3000, sampleRate = 8000), 3000)
        assertTrue(decoded.all { abs(it) == 0f })
    }

    @Test
    fun `plain PCM is refused rather than misread`() {
        val wav = imaAdpcmWav(ShortArray(10), channels = 1, sampleRate = 8000)
        // Rewrite the format tag in place: 1 is PCM.
        wav[20] = 1
        assertFailsWith<IllegalArgumentException> { ImaAdpcmClip.parse(wav) }
    }

    @Test
    fun `a file without its exact length is refused`() {
        val wav = imaAdpcmWav(ShortArray(10), channels = 1, sampleRate = 8000)
        // Rename the fact chunk, so the parser skips it as one it does not know.
        wav[40] = 'x'.code.toByte()
        assertFailsWith<IllegalArgumentException> { ImaAdpcmClip.parse(wav) }
    }
}
