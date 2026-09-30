package at.smiech.engine.impl

import at.smiech.engine.Audio
import at.smiech.engine.LayeredMusic
import at.smiech.engine.Music
import at.smiech.engine.MusicGrid
import at.smiech.engine.Sound
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/**
 * Desktop [Audio] built on javax.sound.sampled.
 *
 * Sounds go through AudioSystem. The JDK only decodes WAV/AIFF/AU; the mp3spi + jlayer service
 * providers on the classpath add MP3, which is what the death sound is. Nothing here is
 * MP3-specific - the SPI does the work. Music is the exception: every piece of it is decoded by the
 * shared [StemMixer], not by AudioSystem; see [Audio.newMusic] and [Audio.newLayeredMusic].
 *
 * @param assetStream opens an asset by filename; each call must return a fresh stream, since
 *   decoding consumes it.
 */
class DesktopAudio(private val assetStream: (String) -> InputStream) : Audio {
    private val sounds = mutableListOf<Sound>()
    private val layered = mutableListOf<LayeredMusic>()

    override fun newMusic(filename: String): Music = TrackMusic.of(clip(filename), ::play)

    override fun newSound(filename: String): Sound =
        DesktopSound(decodeToPcm(assetStream(filename))).also { sounds.add(it) }

    override fun newLayeredMusic(stems: List<String>, grid: MusicGrid): LayeredMusic =
        play(StemMixer(stems.map(::clip), grid))

    override fun dispose() {
        sounds.forEach { it.dispose() }
        // A copy, because each one takes itself off the list as it goes.
        layered.toList().forEach { it.dispose() }
        sounds.clear()
        layered.clear()
    }

    private fun clip(name: String): ImaAdpcmClip = ImaAdpcmClip.parse(assetStream(name).use { it.readBytes() })

    private fun play(mixer: StemMixer): LayeredMusic =
        DesktopLayeredMusic(mixer) { layered.remove(it) }.also { layered.add(it) }
}

/** Decoded PCM audio, held in memory. */
internal class PcmBuffer(val format: AudioFormat, val bytes: ByteArray)

/** Reads [source] through the SPI chain and converts it to plain signed PCM. */
internal fun decodeToPcm(source: InputStream): PcmBuffer {
    AudioSystem.getAudioInputStream(source.buffered()).use { encoded ->
        val target = pcmFormatFor(encoded.format)
        AudioSystem.getAudioInputStream(target, encoded).use { decoded ->
            val out = ByteArrayOutputStream()
            decoded.copyTo(out)
            return PcmBuffer(target, out.toByteArray())
        }
    }
}

private fun pcmFormatFor(source: AudioFormat) = AudioFormat(
    AudioFormat.Encoding.PCM_SIGNED,
    source.sampleRate,
    16,
    source.channels,
    source.channels * 2,
    source.sampleRate,
    false
)

/**
 * Applies a linear 0..1 volume to a line.
 *
 * MASTER_GAIN is a decibel scale, so the mapping is logarithmic. The clamp matters: the game
 * calls `play(100f)`, which Android's SoundPool silently clamps but a FloatControl would throw on.
 */
internal fun setLineVolume(control: FloatControl?, volume: Float) {
    val c = control ?: return
    val clamped = volume.coerceIn(0f, 1f)
    val db = if (clamped <= 0f) c.minimum else (20f * log10(clamped)).coerceIn(c.minimum, c.maximum)
    c.value = db
}

/** Short effect, decoded up front and replayed from a [Clip]. */
internal class DesktopSound(pcm: PcmBuffer) : Sound {
    private val clip: Clip? = runCatching {
        AudioSystem.getClip().apply { open(pcm.format, pcm.bytes, 0, pcm.bytes.size) }
    }.getOrNull()

    override fun play(volume: Float) {
        val c = clip ?: return
        setLineVolume(c.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl, volume)
        c.framePosition = 0
        c.start()
    }

    override fun dispose() {
        clip?.close()
    }
}
