package at.smiech.engine.impl

import android.app.Activity
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import at.smiech.engine.Audio
import at.smiech.engine.Music
import at.smiech.engine.Sound
import java.io.IOException

class AndroidAudio(activity: Activity) : Audio {
    private var assets: AssetManager
    private var soundPool: SoundPool
    private val musicInstances = mutableListOf<Music>()

    override fun newMusic(filename: String): Music {
        return try {
            val afd = assets.openFd(filename)
            AndroidMusic(afd).also { musicInstances.add(it) }
        } catch (exc: IOException) {
            throw RuntimeException("Music-file <$filename> not found! $exc")
        }
    }

    override fun newSound(filename: String): Sound {
        return try {
            val afd = assets.openFd(filename)
            val soundID = soundPool.load(afd, 0)
            AndroidSound(soundID, soundPool)
        } catch (exc: IOException) {
            throw RuntimeException("Sound-file <$filename> not found! $exc")
        }
    }

    override fun dispose() {
        musicInstances.forEach { it.dispose() }
        musicInstances.clear()
        soundPool.release()
    }

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
