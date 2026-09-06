package at.smiech.cyanbat.desktop

import at.smiech.engine.Audio
import at.smiech.engine.Game
import at.smiech.engine.Graphics
import at.smiech.engine.Input
import at.smiech.engine.Screen
import at.smiech.engine.impl.DesktopGraphics
import at.smiech.engine.impl.DesktopInput
import at.smiech.engine.impl.PointerTouchHandler
import at.smiech.engine.impl.SilentAudio
import java.awt.image.BufferedImage

/**
 * Desktop counterpart of AndroidGameActivity: owns the framebuffer and the platform services,
 * and hands the current screen to the shared GameLoop.
 */
class DesktopGame(
    override val frameBufferWidth: Int,
    override val frameBufferHeight: Int,
) : Game {

    val frameBuffer: BufferedImage =
        BufferedImage(frameBufferWidth, frameBufferHeight, BufferedImage.TYPE_INT_RGB)

    val touchHandler = PointerTouchHandler(treatMotionAsDrag = true)

    override val graphics: Graphics =
        DesktopGraphics(frameBuffer) { name ->
            javaClass.getResourceAsStream("/$name")
                ?: error("Asset <$name> not found on the classpath")
        }

    override val audio: Audio = SilentAudio
    override val input: Input = DesktopInput(touchHandler)

    override var currentScreen: Screen? = null
    override val startScreen: Screen? get() = currentScreen

    override fun setScreen(screen: Screen) {
        if (screen === currentScreen) return
        currentScreen?.pause()
        currentScreen?.dispose()
        screen.resume()
        screen.update(0f)
        currentScreen = screen
    }
}
