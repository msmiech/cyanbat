package at.smiech.engine.impl

import at.smiech.engine.Haptics
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/**
 * The phone's Taptic Engine. iOS plays impacts rather than vibrating for a length of time, so every
 * vibration is one heavy impact: the game's are all a hit's, which should be felt over the action.
 * An iPad has no Taptic Engine, and this does nothing there; its Settings leave the switch out.
 *
 * Called on the main thread, where the game loop runs.
 */
class IosHaptics : Haptics {
    private val impact = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)

    override fun vibrate(durationMillis: Long) {
        impact.impactOccurred()
    }
}
