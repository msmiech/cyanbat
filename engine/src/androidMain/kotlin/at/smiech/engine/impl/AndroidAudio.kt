package at.smiech.engine.impl

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import at.smiech.engine.Audio
import at.smiech.engine.LayeredMusic
import at.smiech.engine.Music
import at.smiech.engine.MusicGrid
import at.smiech.engine.Sound
import java.io.IOException

/**
 * Android [Audio]: sounds through a [SoundPool], and every piece of music through the shared
 * [StemMixer], played on an `AudioTrack`; see [Audio.newMusic] and [Audio.newLayeredMusic].
 */
class AndroidAudio(activity: Activity) : Audio {
    private val assets = activity.assets
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(20)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val layeredInstances = mutableListOf<LayeredMusic>()

    init {
        activity.volumeControlStream = AudioManager.STREAM_MUSIC
    }

    override fun newMusic(filename: String): Music = TrackMusic.of(clip(filename), ::play)

    override fun newSound(filename: String): Sound {
        return try {
            // SoundPool duplicates the descriptor, so this one can be closed at once.
            val soundId = assets.openFd(filename).use { soundPool.load(it, 0) }
            AndroidSound(soundId, soundPool)
        } catch (exc: IOException) {
            throw IllegalStateException("Sound $filename could not be loaded", exc)
        }
    }

    override fun newLayeredMusic(stems: List<String>, grid: MusicGrid): LayeredMusic =
        play(StemMixer(stems.map(::clip), grid))

    override fun dispose() {
        // Iterates a copy, because each instance removes itself from the list as it is disposed.
        layeredInstances.toList().forEach { it.dispose() }
        layeredInstances.clear()
        soundPool.release()
    }

    private fun clip(name: String): ImaAdpcmClip =
        try {
            ImaAdpcmClip.parse(assets.open(name).use { it.readBytes() })
        } catch (exc: IOException) {
            throw IllegalStateException("Music $name could not be loaded", exc)
        }

    private fun play(mixer: StemMixer): LayeredMusic =
        AndroidLayeredMusic(mixer) { layeredInstances.remove(it) }.also { layeredInstances.add(it) }
}
