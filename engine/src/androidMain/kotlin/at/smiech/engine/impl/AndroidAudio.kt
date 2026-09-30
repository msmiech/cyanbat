package at.smiech.engine.impl

import android.app.Activity
import android.content.res.AssetManager
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
    private var assets: AssetManager
    private var soundPool: SoundPool
    private val layeredInstances = mutableListOf<LayeredMusic>()

    override fun newMusic(filename: String): Music = TrackMusic.of(clip(filename), ::play)

    override fun newSound(filename: String): Sound {
        return try {
            // SoundPool keeps its own duplicate of the descriptor, so this one can go at once.
            val soundID = assets.openFd(filename).use { soundPool.load(it, 0) }
            AndroidSound(soundID, soundPool)
        } catch (exc: IOException) {
            throw RuntimeException("Sound-file <$filename> not found! $exc")
        }
    }

    override fun newLayeredMusic(stems: List<String>, grid: MusicGrid): LayeredMusic =
        play(StemMixer(stems.map(::clip), grid))

    override fun dispose() {
        // A copy, because each one takes itself off the list as it goes.
        layeredInstances.toList().forEach { it.dispose() }
        layeredInstances.clear()
        soundPool.release()
    }

    private fun clip(name: String): ImaAdpcmClip =
        try {
            ImaAdpcmClip.parse(assets.open(name).use { it.readBytes() })
        } catch (exc: IOException) {
            throw RuntimeException("Music <$name> not found! $exc")
        }

    private fun play(mixer: StemMixer): LayeredMusic =
        AndroidLayeredMusic(mixer) { layeredInstances.remove(it) }.also { layeredInstances.add(it) }

    init {
        activity.volumeControlStream = AudioManager.STREAM_MUSIC
        assets = activity.assets
        SoundPool.Builder().apply {
            setMaxStreams(20)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }.build().also { this.soundPool = it }
    }
}
