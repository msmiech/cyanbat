package at.smiech.engine.impl

import at.smiech.engine.Audio
import at.smiech.engine.LayeredMusic
import at.smiech.engine.Music
import at.smiech.engine.MusicGrid
import at.smiech.engine.Sound
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/**
 * Desktop [Audio] built on javax.sound.sampled.
 *
 * Sounds go through AudioSystem, which decodes the effects' plain PCM WAVs itself. Music is decoded
 * by the shared [StemMixer] instead; see [Audio.newMusic] and [Audio.newLayeredMusic].
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
        // Iterates a copy, because each instance removes itself from the list as it is disposed.
        layered.toList().forEach { it.dispose() }
        sounds.clear()
        layered.clear()
    }

    private fun clip(name: String): ImaAdpcmClip =
        ImaAdpcmClip.parse(assetStream(name).use { it.readBytes() })

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
 * MASTER_GAIN is in decibels, so the mapping is logarithmic. The clamp matters: a FloatControl
 * throws on a value out of its range, where Android's SoundPool clamps silently.
 */
internal fun setLineVolume(control: FloatControl?, volume: Float) {
    val c = control ?: return
    val clamped = volume.coerceIn(0f, 1f)
    val db = if (clamped <= 0f) c.minimum else (20f * log10(clamped)).coerceIn(c.minimum, c.maximum)
    c.value = db
}

/**
 * A short effect, decoded up front and replayed from a [Clip]. Silent while no line is available.
 *
 * Played on the game's thread, so nothing a sound device does may throw out of it: a clip that
 * fails, as one can once the system's output has changed under it, is let go of, and another is
 * opened, at most every [REOPEN_MILLIS], on the next play. One that could not be opened as the run
 * started is opened that way too, once a device takes it.
 */
internal class DesktopSound(private val pcm: PcmBuffer) : Sound {
    private var clip: Clip? = open()

    /** When [play] may next try to open a clip, from [System.nanoTime]. */
    private var reopenAt = 0L

    private var disposed = false

    override fun play(volume: Float) {
        if (disposed) return
        val c = clip ?: reopen() ?: return
        try {
            if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                setLineVolume(c.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl, volume)
            }
            c.framePosition = 0
            c.start()
        } catch (exc: Exception) {
            System.err.println("CyanBat: sound effect failed, reopening - $exc")
            c.close()
            clip = null
            SoundDevices.lineFailed()
        }
    }

    private fun reopen(): Clip? {
        val now = System.nanoTime()
        if (now < reopenAt) return null
        reopenAt = now + TimeUnit.MILLISECONDS.toNanos(REOPEN_MILLIS)
        return open().also { clip = it }
    }

    private fun open(): Clip? {
        var opened: Clip? = null
        return try {
            opened = AudioSystem.getClip()
            opened.open(pcm.format, pcm.bytes, 0, pcm.bytes.size)
            opened
        } catch (_: Exception) {
            opened?.close()
            SoundDevices.lineFailed()
            null
        }
    }

    override fun dispose() {
        disposed = true
        clip?.close()
        clip = null
    }

    private companion object {
        /** The least time between two tries at opening a clip, which costs the game's thread. */
        const val REOPEN_MILLIS = 2_000L
    }
}
