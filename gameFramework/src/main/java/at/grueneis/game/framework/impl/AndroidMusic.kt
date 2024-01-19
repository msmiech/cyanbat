package at.grueneis.game.framework.impl

import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import at.grueneis.game.framework.Music
import java.io.IOException

class AndroidMusic(afd: AssetFileDescriptor) : Music, OnCompletionListener {
    var mediaPlayer: MediaPlayer = MediaPlayer()
    var isPrepared = false
    override fun onCompletion(arg0: MediaPlayer) {
        synchronized(this) { isPrepared = false }
    }

    override fun play() {
        if (mediaPlayer.isPlaying) return
        try {
            synchronized(this) {
                if (!isPrepared) mediaPlayer.prepare()
                mediaPlayer.start()
            }
        } catch (exc: Exception) {
            exc.printStackTrace()
        }
    }

    override fun stop() {
        mediaPlayer.stop()
        synchronized(this) { isPrepared = false }
    }

    override fun pause() {
        mediaPlayer.pause()
    }

    override fun setVolume(volume: Float) {
        mediaPlayer.setVolume(volume, volume)
    }

    override val isPlaying: Boolean
        get() = mediaPlayer.isPlaying
    override val isStopped: Boolean
        get() = !isPrepared
    override var isLooping: Boolean
        get() = mediaPlayer.isLooping
        set(looping) {
            mediaPlayer.isLooping = looping
        }

    override fun dispose() {
        if (mediaPlayer.isPlaying) mediaPlayer.stop()
        mediaPlayer.release()
    }

    init {
        try {
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer.prepare()
            isPrepared = true
            mediaPlayer.setOnCompletionListener(this)
        } catch (exc: IOException) {
            throw RuntimeException("Could not load music!")
        }
    }
}