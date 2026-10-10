package at.smiech.engine.impl

import at.smiech.engine.Audio
import at.smiech.engine.Game
import at.smiech.engine.Input
import at.smiech.engine.Screen

/**
 * A [Game] hosted in a Compose window, which [GameSurface] draws and drives: the desktop's and the
 * browser's. Holds the platform services and the current screen; a host supplies the pieces that
 * differ, which are where the pictures and the sounds come from.
 *
 * Android's activity is its own [Game], with its own surface, since it takes input and the
 * lifecycle from the activity rather than from Compose alone.
 */
open class ComposeGame(
    final override val frameBufferWidth: Int,
    final override val frameBufferHeight: Int,
    final override val graphics: ComposeGraphics,
    final override val audio: Audio,
    /**
     * Keyboard state. Passed in rather than created here because the window that receives the key
     * events outlives any single game instance.
     */
    val controlHandler: ControlHandler = ControlHandler(),
) : Game {

    /** A mouse steers by moving, with no button held; see [PointerTouchHandler]. */
    val touchHandler = PointerTouchHandler(treatMotionAsDrag = true)

    final override val input: Input = ComposeInput(touchHandler, controlHandler)

    final override var currentScreen: Screen? = null

    final override fun setScreen(screen: Screen) {
        if (screen === currentScreen) return
        currentScreen?.pause()
        currentScreen?.dispose()
        screen.resume()
        screen.update(0f)
        currentScreen = screen
    }
}
