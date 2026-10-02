package at.smiech.cyanbat.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.createFontFamilyResolver
import at.smiech.engine.Audio
import at.smiech.engine.Game
import at.smiech.engine.Input
import at.smiech.engine.Screen
import at.smiech.engine.impl.ComposeGraphics
import at.smiech.engine.impl.ControlHandler
import at.smiech.engine.impl.DesktopAudio
import at.smiech.engine.impl.DesktopInput
import at.smiech.engine.impl.PointerTouchHandler
import org.jetbrains.skia.Image

/**
 * Desktop counterpart of AndroidGameActivity: owns the platform services, hands the current screen
 * to the shared GameLoop, and keeps the frame it last presented for [GameSurface] to draw.
 */
class DesktopGame(
    override val frameBufferWidth: Int,
    override val frameBufferHeight: Int,
    /**
     * Keyboard state. Passed in rather than created here because the window that receives the
     * key events outlives any single game instance.
     */
    val controlHandler: ControlHandler = ControlHandler(),
) : Game {

    val touchHandler = PointerTouchHandler(treatMotionAsDrag = true)

    override val graphics = ComposeGraphics(
        frameBufferWidth,
        frameBufferHeight,
        ::loadImage,
        createFontFamilyResolver(),
    )

    override val audio: Audio = DesktopAudio(::openAsset)
    override val input: Input = DesktopInput(touchHandler, controlHandler)

    override var currentScreen: Screen? = null
    override val startScreen: Screen? get() = currentScreen

    /** Where [capture] draws the frame, kept from one capture to the next. */
    private val captured by lazy { ImageBitmap(frameBufferWidth, frameBufferHeight) }
    private val capturedPixels by lazy { IntArray(frameBufferWidth * frameBufferHeight) }

    /**
     * The frame the current screen last presented, drawn at the frame's own size, as ARGB pixels a
     * row at a time: what the recorder tapes and the tests read. The next capture reuses the array.
     */
    fun capture(): IntArray {
        graphics.drawInto(captured)
        captured.readPixels(capturedPixels)
        return capturedPixels
    }

    /** Assets ride along as classpath resources; see the resources srcDir in build.gradle.kts. */
    private fun openAsset(name: String): java.io.InputStream =
        javaClass.getResourceAsStream("/$name") ?: error("Asset <$name> not found on the classpath")

    /**
     * An asset decoded by Skia and marked immutable, which is a matter of speed and nothing else.
     * Compose's desktop canvas wraps a bitmap in a fresh Skia image every time it draws it, and
     * copies the whole of one that could still change to do it: every strip of the desert's ground,
     * every frame, was a copy of the whole sheet.
     */
    private fun loadImage(name: String): ImageBitmap {
        val bytes = openAsset(name).use { it.readBytes() }
        return Image.makeFromEncoded(bytes).toComposeImageBitmap().also { it.asSkiaBitmap().setImmutable() }
    }

    override fun setScreen(screen: Screen) {
        if (screen === currentScreen) return
        currentScreen?.pause()
        currentScreen?.dispose()
        screen.resume()
        screen.update(0f)
        currentScreen = screen
    }
}
