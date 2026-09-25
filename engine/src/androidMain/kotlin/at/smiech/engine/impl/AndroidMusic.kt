package at.smiech.engine.impl

import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import at.smiech.engine.Music
import java.io.IOException

class AndroidMusic(afd: AssetFileDescriptor) : Music, OnCompletionListener {
    val mediaPlayer: MediaPlayer = MediaPlayer()
    var isPrepared = false

    /**
     * Set when a non-looping track runs out. The player is still prepared at that point - it sits
     * in PlaybackCompleted, from which start() replays from the top - so this is tracked on its
     * own rather than by clearing [isPrepared]. Clearing it sent the next [play] into prepare(),
     * which that state does not allow, and the replay failed silently.
     */
    private var completed = false

    /**
     * Once released, every MediaPlayer call throws, isPlaying included. A track can be disposed
     * by more than one owner - the menu's ViewModel and the activity's Audio both do it - so
     * everything after the first dispose has to be a no-op rather than a crash on the way out.
     */
    private var released = false

    override fun onCompletion(arg0: MediaPlayer) {
        synchronized(this) { completed = true }
    }

    override fun play() {
        synchronized(this) {
            if (released || mediaPlayer.isPlaying) return
            try {
                if (!isPrepared) {
                    mediaPlayer.prepare()
                    isPrepared = true
                }
                completed = false
                mediaPlayer.start()
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }
    }

    override fun stop() {
        synchronized(this) {
            if (released) return
            mediaPlayer.stop()
            isPrepared = false
            completed = false
        }
    }

    override fun pause() {
        synchronized(this) {
            if (released) return
            mediaPlayer.pause()
        }
    }

    override fun setVolume(volume: Float) {
        synchronized(this) {
            if (released) return
            mediaPlayer.setVolume(volume, volume)
        }
    }

    override val isPlaying: Boolean
        get() = synchronized(this) { !released && mediaPlayer.isPlaying }
    override val isStopped: Boolean
        get() = synchronized(this) { released || !isPrepared || completed }
    override var isLooping: Boolean
        get() = synchronized(this) { !released && mediaPlayer.isLooping }
        set(looping) {
            synchronized(this) {
                if (!released) mediaPlayer.isLooping = looping
            }
        }

    override fun dispose() {
        synchronized(this) {
            if (released) return
            released = true
            // release() stops playback itself, in whatever state the player is in.
            mediaPlayer.release()
        }
    }

    init {
        try {
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer.prepare()
            isPrepared = true
            mediaPlayer.setOnCompletionListener(this)
        } catch (exc: IOException) {
            mediaPlayer.release()
            throw RuntimeException("Could not load music! $exc")
        } finally {
            afd.close()
        }
    }
}
