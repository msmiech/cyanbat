package at.smiech.engine.impl

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.setActive
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

/**
 * The app's one audio engine, which every piece of music plays through, each on a player node of
 * its own: the iOS counterpart of the page's one AudioContext (WebAudioDevice).
 *
 * The session is ambient: the game's sound mixes with whatever else the player has playing, and
 * the ring/silent switch silences it, as it does a casual game's.
 *
 * iOS stops the engine for a phone call or an alarm, and when the output changes - headphones
 * plugged in or out, a Bluetooth speaker coming and going. The engine is started again once the
 * interruption is over, or the app comes back to the front, and every piece of music that was
 * sounding is set going on it again ([IosLayeredMusic.restart]).
 *
 * Make one per app, on the main thread, and keep it for the app's life.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioDevice {
    private val engine = AVAudioEngine()

    /** Every piece of music attached to the engine. Main thread only. */
    private val players = mutableSetOf<IosLayeredMusic>()

    init {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryAmbient, error = null)
        session.setActive(true, error = null)
        // Reading the main mixer builds the graph's output side, which the engine will not start
        // without.
        engine.mainMixerNode
        engine.prepare()

        val center = NSNotificationCenter.defaultCenter
        val main = NSOperationQueue.mainQueue
        center.addObserverForName(AVAudioSessionInterruptionNotification, null, main) { note ->
            val type = (note?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)
                ?.unsignedLongValue
            if (type == AVAudioSessionInterruptionTypeEnded) restart()
        }
        center.addObserverForName(AVAudioEngineConfigurationChangeNotification, engine, main) { _ ->
            restart()
        }
        // An interruption's end is not always posted: an app sent to the background meanwhile may
        // only hear of it by coming back.
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, main) { _ ->
            restart()
        }
    }

    /**
     * Starts the engine if it is not running, and says whether it is. Called from any music's queue
     * before it starts its player, which throws on an engine that is not running.
     */
    internal fun ensureRunning(): Boolean {
        if (engine.running) return true
        AVAudioSession.sharedInstance().setActive(true, error = null)
        return engine.startAndReturnError(null)
    }

    /** Puts [music]'s [player] on the engine, playing [format]. Main thread. */
    internal fun attach(music: IosLayeredMusic, player: AVAudioPlayerNode, format: AVAudioFormat) {
        engine.attachNode(player)
        engine.connect(player, engine.mainMixerNode, format)
        players += music
    }

    /** Takes [music]'s [player] off the engine. */
    internal fun detach(music: IosLayeredMusic, player: AVAudioPlayerNode) {
        engine.detachNode(player)
        players -= music
    }

    /** Starts the engine again, and the music that was sounding on it. Main thread. */
    private fun restart() {
        if (!ensureRunning()) return
        players.toList().forEach { it.restart() }
    }
}
