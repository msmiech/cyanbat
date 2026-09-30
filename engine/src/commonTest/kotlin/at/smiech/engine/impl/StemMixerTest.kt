package at.smiech.engine.impl

import at.smiech.engine.MusicGrid
import at.smiech.engine.Quantum
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The mixer's promises about time: where on the grid a change lands, and that stems of different
 * lengths stay in step. Stems here are constants wherever they can be, since IMA ADPCM carries a
 * constant exactly and a level can then be read straight off the output.
 */
class StemMixerTest {

    /** 120 beats a minute at 8 kHz: a beat is 4000 frames and a bar of four is 16000. */
    private val rate = 8000
    private val grid = MusicGrid(beatsPerMinute = 120.0, beatsPerBar = 4)
    private val beat = 4000

    private fun render(mixer: StemMixer, frames: Int): FloatArray {
        val pcm = ShortArray(frames * 2)
        mixer.render(pcm, frames)
        return FloatArray(frames) { pcm[2 * it] / 32767f }
    }

    private fun quarterLevel() = StemMixer(listOf(constantClip(8192, frames = 64000, sampleRate = rate)), grid)

    @Test
    fun `a layer is silent until it is asked for`() {
        val out = render(quarterLevel(), 5000)
        assertTrue(out.all { it == 0f })
    }

    /** Fully up on the beat, where its first note lands, having faded in over the moment before. */
    @Test
    fun `a layer coming in is all the way up on the next beat`() {
        val mixer = quarterLevel()
        mixer.setLayerLevel(0, 1f, Quantum.BEAT, fadeBeats = 0.125f)
        val out = render(mixer, 2 * beat)

        val fade = beat / 8
        assertEquals(0f, out[beat - fade - 1], "still silent before the fade")
        assertEquals(0.125f, out[beat - fade / 2], 0.01f, "halfway up halfway through the fade")
        assertEquals(0.25f, out[beat], 0.001f, "all the way up on the beat")
    }

    /** The beat it leaves on still sounds; the fade comes after. */
    @Test
    fun `a layer going out plays out its beat first`() {
        val mixer = quarterLevel()
        mixer.setLayerLevel(0, 1f, Quantum.IMMEDIATE, fadeBeats = 0f)
        render(mixer, 1000)
        mixer.setLayerLevel(0, 0f, Quantum.BEAT, fadeBeats = 0.25f)
        val out = render(mixer, 2 * beat)

        // Frame 0 of this render is frame 1000 of the music.
        assertEquals(0.25f, out[beat - 1000 - 1], 0.001f, "full up to the beat")
        assertEquals(0f, out[beat - 1000 + beat / 4 + 1], "gone once the fade is over")
    }

    @Test
    fun `a bar order waits for the downbeat`() {
        val mixer = quarterLevel()
        render(mixer, 100)
        mixer.setLayerLevel(0, 1f, Quantum.BAR, fadeBeats = 0.0625f)
        val out = render(mixer, 20000)

        val bar = 4 * beat
        val fade = beat / 16
        assertEquals(0f, out[beat - 100], "not on the next beat")
        assertEquals(0f, out[bar - fade - 100 - 1], "nor anywhere before the bar's fade")
        assertEquals(0.25f, out[bar - 100], 0.001f, "but on the downbeat")
    }

    /**
     * The latest order wins, and one that changes its mind before the boundary costs nothing: a
     * layer told to leave and then to stay again never dips.
     */
    @Test
    fun `an order replaced before its beat never happens`() {
        val mixer = quarterLevel()
        mixer.setLayerLevel(0, 1f, Quantum.IMMEDIATE, fadeBeats = 0f)
        render(mixer, 1000)
        mixer.setLayerLevel(0, 0f, Quantum.BEAT, fadeBeats = 0.25f)
        render(mixer, 1000)
        mixer.setLayerLevel(0, 1f, Quantum.BEAT, fadeBeats = 0.25f)
        val out = render(mixer, 2 * beat)
        assertTrue(out.all { abs(it - 0.25f) < 0.001f }, "held at full throughout")
    }

    @Test
    fun `layers add up`() {
        val mixer = StemMixer(
            listOf(
                constantClip(8192, frames = 8000, sampleRate = rate),
                constantClip(4096, frames = 16000, sampleRate = rate),
            ),
            grid,
        )
        mixer.setLayerLevel(0, 1f, Quantum.IMMEDIATE, 0f)
        mixer.setLayerLevel(1, 0.5f, Quantum.IMMEDIATE, 0f)
        val out = render(mixer, 1000)
        assertEquals(0.25f + 0.0625f, out[500], 0.001f)
    }

    /**
     * A one-bar loop under a four-bar one: after several times round the long one, the two still
     * start together. Each stem carries a mark on its first frame, and the marks have to coincide.
     */
    @Test
    fun `stems of different lengths stay in step`() {
        val bar = 4 * beat
        fun marked(frames: Int, mark: Short) =
            ImaAdpcmClip.parse(
                imaAdpcmWav(ShortArray(frames) { if (it == 0) mark else 0 }, channels = 1, sampleRate = rate)
            )
        val mixer = StemMixer(listOf(marked(bar, 8000), marked(4 * bar, 16000)), grid)
        mixer.setLayerLevel(0, 1f, Quantum.IMMEDIATE, 0f)
        mixer.setLayerLevel(1, 1f, Quantum.IMMEDIATE, 0f)
        val out = render(mixer, 12 * bar + 10)

        // From the second bar: on the first, both are still in their few milliseconds of fade-in.
        for (loop in 1..12) {
            val expected = if (loop % 4 == 0) 24000 / 32768f else 8000 / 32768f
            assertEquals(expected, out[loop * bar], 0.002f, "the mark at bar $loop")
        }
    }

    @Test
    fun `muffling takes the treble out and leaves the bass`() {
        fun level(hz: Double, muffle: Float): Double {
            val tone = ShortArray(22050) { (sin(2 * PI * hz * it / 22050) * 0.5 * 32767).toInt().toShort() }
            val mixer = StemMixer(
                listOf(ImaAdpcmClip.parse(imaAdpcmWav(tone, channels = 1, sampleRate = 22050))),
                grid,
            )
            mixer.setLayerLevel(0, 1f, Quantum.IMMEDIATE, 0f)
            mixer.setMuffle(muffle)
            render(mixer, 11025) // let the glide settle
            val out = render(mixer, 4410)
            return sqrt(out.sumOf { (it * it).toDouble() } / out.size)
        }

        val clearTreble = level(4000.0, 0f)
        val muffledTreble = level(4000.0, 1f)
        val clearBass = level(80.0, 0f)
        val muffledBass = level(80.0, 1f)
        assertTrue(muffledTreble < clearTreble / 20, "4 kHz: $clearTreble clear, $muffledTreble muffled")
        assertTrue(muffledBass > clearBass * 0.7, "80 Hz: $clearBass clear, $muffledBass muffled")
    }

    @Test
    fun `volume scales the whole mix`() {
        val mixer = quarterLevel()
        mixer.setLayerLevel(0, 1f, Quantum.IMMEDIATE, 0f)
        mixer.setVolume(0.5f)
        render(mixer, 4000) // let the glide settle
        val out = render(mixer, 100)
        assertEquals(0.125f, out[50], 0.002f)
    }

    /** What a platform waits on before it pauses its device, so the pause does not click. */
    @Test
    fun `the transport fades out to silence and back`() {
        val mixer = quarterLevel()
        mixer.setLayerLevel(0, 1f, Quantum.IMMEDIATE, 0f)
        render(mixer, 1000)

        mixer.fadeTransport(on = false)
        val fading = render(mixer, 400)
        assertTrue(mixer.isSilenced)
        assertTrue(fading.first() > 0.2f && fading.last() == 0f, "faded, not cut")

        mixer.fadeTransport(on = true)
        val back = render(mixer, 400)
        assertTrue(back.first() < 0.05f && abs(back.last() - 0.25f) < 0.001f, "and faded back up")
    }
}
