package at.smiech.engine.impl

import at.smiech.engine.Audio
import at.smiech.engine.LayeredMusic
import at.smiech.engine.Music
import at.smiech.engine.MusicGrid
import at.smiech.engine.Sound
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.await
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray

/**
 * [Audio] in a browser, on the Web Audio API, through the page's [device].
 *
 * Effects are decoded by the browser from the files [assets] preloaded, the death sound's MP3
 * included, and are silent for the moment that takes. Music is decoded by the shared [StemMixer],
 * as on every platform, and played by [WebLayeredMusic]; its files are fetched as each piece is
 * made, so a piece is a [PendingMusic] or a [PendingLayeredMusic] until they arrive.
 *
 * Everything plays through one node of this audio's own, so [dispose] silences all of it at once,
 * however much is already scheduled.
 */
class WebAudio(device: WebAudioDevice, private val assets: WebAssets) : Audio {
    private val context = device.context
    private val output = context.createGain().apply { connect(context.destination) }

    // A failed download leaves its music silent rather than taking the game down with it.
    private val scope = CoroutineScope(
        SupervisorJob() + CoroutineExceptionHandler { _, exc ->
            println("CyanBat: audio unavailable - $exc")
        }
    )
    private val sounds = mutableListOf<Sound>()
    private val tracks = mutableListOf<PendingMusic>()
    private val pieces = mutableListOf<PendingLayeredMusic>()

    override fun newMusic(filename: String): Music {
        val track = PendingMusic().also { tracks.add(it) }
        scope.launch {
            val clip = ImaAdpcmClip.parse(fetchBytes(filename))
            track.arrive(TrackMusic.of(clip, ::play))
        }
        return track
    }

    override fun newSound(filename: String): Sound {
        val sound = WebSound(context, output).also { sounds.add(it) }
        scope.launch {
            // Decoding takes the buffer it is handed away from the page, so it gets a copy, and the
            // asset stays for the next run.
            sound.decoded = context.decodeAudioData(assets.buffer(filename).slice(0)).await()
        }
        return sound
    }

    override fun newLayeredMusic(stems: List<String>, grid: MusicGrid): LayeredMusic {
        val piece = PendingLayeredMusic(stems.size).also { pieces.add(it) }
        scope.launch {
            val clips = stems.map { async { ImaAdpcmClip.parse(fetchBytes(it)) } }.awaitAll()
            piece.arrive(play(StemMixer(clips, grid)))
        }
        return piece
    }

    override fun dispose() {
        scope.cancel()
        sounds.forEach { it.dispose() }
        tracks.forEach { it.dispose() }
        pieces.forEach { it.dispose() }
        sounds.clear()
        tracks.clear()
        pieces.clear()
        output.disconnect()
    }

    private suspend fun fetchBytes(name: String): ByteArray =
        Int8Array(assets.fetch(name)).toByteArray()

    private fun play(mixer: StemMixer): LayeredMusic = WebLayeredMusic(context, output, mixer)
}

/**
 * A short effect, played from a fresh source each time, which a browser makes cheaply. Silent until
 * it is [decoded].
 */
internal class WebSound(private val context: AudioContext, private val output: AudioNode) : Sound {
    var decoded: AudioBuffer? = null

    override fun play(volume: Float) {
        val buffer = decoded ?: return
        // A suspended context would hold every effect played meanwhile and burst them all out on
        // resuming.
        if (context.state != "running") return
        val gain = context.createGain()
        gain.gain.value = volume.coerceIn(0f, 1f)
        gain.connect(output)
        val source = context.createBufferSource()
        source.buffer = buffer
        source.connect(gain)
        source.start(0.0)
    }

    override fun dispose() {
        decoded = null
    }
}
