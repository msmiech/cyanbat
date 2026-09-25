package at.smiech.engine.impl

import at.smiech.engine.Audio
import at.smiech.engine.Music
import at.smiech.engine.Sound
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

/**
 * Desktop [Audio] built on javax.sound.sampled.
 *
 * The JDK only decodes WAV/AIFF/AU; the mp3spi + jlayer service providers on the classpath add
 * MP3, which is what all of this game's audio is. Nothing here is MP3-specific - it goes through
 * AudioSystem, so the SPI does the work.
 *
 * @param assetStream opens an asset by filename; each call must return a fresh stream, since
 *   decoding consumes it and looping re-reads from the start.
 */
class DesktopAudio(private val assetStream: (String) -> InputStream) : Audio {
    private val music = mutableListOf<Music>()
    private val sounds = mutableListOf<Sound>()

    override fun newMusic(filename: String): Music =
        DesktopMusic { assetStream(filename) }.also { music.add(it) }

    override fun newSound(filename: String): Sound =
        DesktopSound(decodeToPcm(assetStream(filename))).also { sounds.add(it) }

    override fun dispose() {
        music.forEach { it.dispose() }
        sounds.forEach { it.dispose() }
        music.clear()
        sounds.clear()
    }
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

/**
 * Streamed track, played on a daemon thread so it never holds the JVM open.
 *
 * Looping reopens the stream at EOF rather than seeking, because the decoded stream is forward
 * only.
 *
 * Each playback thread is stamped with a generation, and only the current one may touch the
 * shared state. [stop] cannot wait for its thread - a line write blocks and is not interruptible -
 * so a [play] right behind it starts a new thread while the old one is still unwinding. Without
 * the stamp the old thread saw the flags the new play had just reset and either kept writing,
 * doubling the track, or ran its cleanup and stopped the new one.
 */
class DesktopMusic(private val openStream: () -> InputStream) : Music {
    private val lock = Any()
    private var line: SourceDataLine? = null
    private var thread: Thread? = null

    /** Bumped by every [play] that starts a thread and by every [stop]. */
    @Volatile private var generation = 0

    @Volatile private var playing = false
    @Volatile private var stopped = true
    @Volatile private var disposed = false
    @Volatile private var volume = 1f

    override var isLooping: Boolean = false

    override val isPlaying: Boolean get() = playing
    override val isStopped: Boolean get() = stopped

    override fun play() {
        synchronized(lock) {
            if (disposed || playing) return
            playing = true
            // Paused rather than stopped: the thread is still there, waiting to carry on.
            if (!stopped && thread?.isAlive == true) return
            stopped = false
            val current = ++generation
            thread = Thread({ pump(current) }, "cyanbat-music").apply {
                isDaemon = true
                start()
            }
        }
    }

    override fun pause() {
        synchronized(lock) {
            playing = false
            line?.stop()
        }
    }

    override fun stop() {
        synchronized(lock) {
            playing = false
            stopped = true
            generation++
            thread?.interrupt()
            thread = null
        }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        applyVolume()
    }

    override fun dispose() {
        synchronized(lock) {
            disposed = true
            stop()
            line?.close()
            line = null
        }
    }

    private fun isCurrent(stamp: Int) = stamp == generation && !disposed

    private fun applyVolume() {
        setLineVolume(line?.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl, volume)
    }

    private fun pump(stamp: Int) {
        try {
            do {
                val pcm = decodeToPcm(openStream())
                val out = AudioSystem.getSourceDataLine(pcm.format).apply {
                    open(pcm.format)
                    start()
                }
                try {
                    synchronized(lock) {
                        if (!isCurrent(stamp)) return
                        line = out
                    }
                    applyVolume()

                    var offset = 0
                    val chunk = 4096
                    while (offset < pcm.bytes.size && isCurrent(stamp)) {
                        if (!playing) {
                            Thread.sleep(PAUSE_POLL_MILLIS)
                            continue
                        }
                        if (!out.isRunning) out.start()
                        val len = minOf(chunk, pcm.bytes.size - offset)
                        offset += out.write(pcm.bytes, offset, len)
                    }
                    // Let a track that ran to its end finish sounding; one that was stopped is
                    // cut off at once.
                    if (isCurrent(stamp)) out.drain()
                } finally {
                    synchronized(lock) { if (line === out) line = null }
                    out.close()
                }
            } while (isLooping && isCurrent(stamp))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (exc: Exception) {
            // A missing codec or an unavailable mixer must not take the game down with it.
            System.err.println("CyanBat: music playback stopped - $exc")
        } finally {
            // Only the current thread speaks for the track. A superseded one finishing must not
            // mark a newer playback as stopped.
            synchronized(lock) {
                if (stamp == generation) {
                    playing = false
                    stopped = true
                    thread = null
                }
            }
        }
    }

    private companion object {
        const val PAUSE_POLL_MILLIS = 50L
    }
}
