package at.smiech.engine.impl

import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import at.smiech.engine.Music
import java.io.IOException

class AndroidMusic(afd: AssetFileDescriptor) : Music, OnCompletionListener {
    val mediaPlayer: MediaPlayer = MediaPlayer()
    var isPrepared = false
    override fun onCompletion(arg0: MediaPlayer) {
        synchronized(this) { isPrepared = false }
    }

    override fun play() {
        synchronized(this) {
            if (mediaPlayer.isPlaying) return
            try {
                if (!isPrepared) {
                    mediaPlayer.prepare()
                    isPrepared = true
                }

                mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(1.0f)
                
                mediaPlayer.start()
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }
    }

    override fun stop() {
        synchronized(this) {
            mediaPlayer.stop()
            isPrepared = false
        }
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
            afd.close()
            mediaPlayer.prepare()
            isPrepared = true
            mediaPlayer.setOnCompletionListener(this)
        } catch (exc: IOException) {
            throw RuntimeException("Could not load music! $exc")
        }
    }
}
