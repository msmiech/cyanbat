package at.smiech.engine.impl

import at.smiech.engine.Audio
import at.smiech.engine.Music
import at.smiech.engine.Sound

/**
 * Placeholder [Audio] that plays nothing.
 *
 * The game's audio assets are MP3, which the JDK's javax.sound.sampled cannot decode without a
 * third-party service provider. This keeps the desktop build playable until that is wired up.
 */
object SilentAudio : Audio {
    override fun newMusic(filename: String): Music = SilentMusic()
    override fun newSound(filename: String): Sound = SilentSound
    override fun dispose() = Unit
}

class SilentMusic : Music {
    override fun play() = Unit
    override fun stop() = Unit
    override fun pause() = Unit
    override fun setVolume(volume: Float) = Unit
    override val isPlaying: Boolean get() = false
    override val isStopped: Boolean get() = true
    override var isLooping: Boolean = false
    override fun dispose() = Unit
}

object SilentSound : Sound {
    override fun play(volume: Float) = Unit
    override fun dispose() = Unit
}
