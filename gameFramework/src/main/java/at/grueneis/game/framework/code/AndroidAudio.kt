package at.grueneis.game.framework.code

import android.app.Activity
import android.content.res.AssetManager
import android.media.AudioManager
import android.media.SoundPool
import at.grueneis.game.framework.Audio
import at.grueneis.game.framework.Music
import at.grueneis.game.framework.Sound
import java.io.IOException

class AndroidAudio(activity: Activity) : Audio {
    var assets: AssetManager
    var soundPool: SoundPool
    override fun newMusic(filename: String): Music {
        return try {
            val afd = assets.openFd(filename)
            AndroidMusic(afd)
        } catch (exc: IOException) {
            throw RuntimeException("Music-file <$filename> not found!")
        }
    }

    override fun newSound(filename: String): Sound {
        return try {
            val afd = assets.openFd(filename)
            val soundID = soundPool.load(afd, 0)
            AndroidSound(soundID, soundPool)
        } catch (exc: IOException) {
            throw RuntimeException("Sound-file <$filename> not found!")
        }
    }

    init {
        activity.volumeControlStream = AudioManager.STREAM_MUSIC
        assets = activity.assets
        soundPool = SoundPool(20, AudioManager.STREAM_MUSIC, 0)
    }
}