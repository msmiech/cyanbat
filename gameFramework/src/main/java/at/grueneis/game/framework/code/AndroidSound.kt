package at.grueneis.game.framework.code

import android.media.SoundPool
import at.grueneis.game.framework.Sound

class AndroidSound(var soundID: Int, var soundPool: SoundPool) : Sound {
    override fun play(volume: Float) {
        soundPool.play(soundID, volume, volume, 0, 0, 1f)
    }

    override fun dispose() {
        soundPool.unload(soundID)
    }
}