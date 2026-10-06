package at.smiech.engine.impl

import android.media.SoundPool
import at.smiech.engine.Sound

/** A [Sound] loaded into a [SoundPool] as [soundId]. */
class AndroidSound(private val soundId: Int, private val soundPool: SoundPool) : Sound {
    override fun play(volume: Float) {
        soundPool.play(soundId, volume, volume, 0, 0, 1f)
    }

    override fun dispose() {
        soundPool.unload(soundId)
    }
}
