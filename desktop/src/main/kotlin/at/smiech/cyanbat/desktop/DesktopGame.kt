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
     * Keyboard state. Passed in rather than created here because the window that receives the key
     * events outlives any single game instance.
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

    /** Where [capture] draws the frame, kept from one capture to the next. */
    private val captured by lazy { ImageBitmap(frameBufferWidth, frameBufferHeight) }
    private val capturedPixels by lazy { IntArray(frameBufferWidth * frameBufferHeight) }

    /**
     * The frame the current screen last presented, drawn at the frame's own size, as ARGB pixels
     * row by row, for the recorder and the tests. The next capture reuses the array.
     */
    fun capture(): IntArray {
        graphics.drawInto(captured)
        captured.readPixels(capturedPixels)
        return capturedPixels
    }

    /** Assets are classpath resources; see the resources srcDir in build.gradle.kts. */
    private fun openAsset(name: String): java.io.InputStream =
        javaClass.getResourceAsStream("/$name") ?: error("Asset $name not found on the classpath")

    /**
     * An asset decoded by Skia and marked immutable, for speed. Compose's desktop canvas wraps a
     * bitmap in a new Skia image on every draw and copies one that could still change: each strip
     * of the desert's ground was a copy of its whole sheet, every frame.
     */
    private fun loadImage(name: String): ImageBitmap {
        val bytes = openAsset(name).use { it.readBytes() }
        return Image.makeFromEncoded(bytes).toComposeImageBitmap()
            .also { it.asSkiaBitmap().setImmutable() }
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
