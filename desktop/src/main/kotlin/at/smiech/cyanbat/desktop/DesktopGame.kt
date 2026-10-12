package at.smiech.cyanbat.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.createFontFamilyResolver
import at.smiech.engine.impl.ComposeGame
import at.smiech.engine.impl.ComposeGraphics
import at.smiech.engine.impl.ControlHandler
import at.smiech.engine.impl.DesktopAudio
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.InputStream

/**
 * Desktop counterpart of AndroidGameActivity: a [ComposeGame] that loads its assets off the
 * classpath, which the shared GameSurface draws and drives in the window.
 */
class DesktopGame(
    frameBufferWidth: Int,
    frameBufferHeight: Int,
    controlHandler: ControlHandler = ControlHandler(),
) : ComposeGame(
    frameBufferWidth,
    frameBufferHeight,
    ComposeGraphics(
        frameBufferWidth,
        frameBufferHeight,
        ::loadImage,
        createFontFamilyResolver(),
        ::makeImage,
    ),
    DesktopAudio(::openAsset),
    controlHandler,
) {
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
}

/** Assets are classpath resources; see the resources srcDir in build.gradle.kts. */
private fun openAsset(name: String): InputStream =
    DesktopGame::class.java.getResourceAsStream("/$name")
        ?: error("Asset $name not found on the classpath")

/**
 * An asset decoded by Skia and marked immutable, for speed. Compose's desktop canvas wraps a bitmap
 * in a new Skia image on every draw and copies one that could still change: each strip of the
 * desert's ground was a copy of its whole sheet, every frame.
 */
private fun loadImage(name: String): ImageBitmap {
    val bytes = openAsset(name).use { it.readBytes() }
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
        .also { it.asSkiaBitmap().setImmutable() }
}

/**
 * Pixels the game made, as an immutable Skia image like the assets, for the same reason. ARGB ints
 * laid out little-endian are BGRA bytes.
 */
internal fun makeImage(width: Int, height: Int, pixels: IntArray): ImageBitmap {
    val bytes = ByteArray(width * height * 4)
    for (i in 0 until width * height) {
        val argb = pixels[i]
        bytes[i * 4] = argb.toByte()
        bytes[i * 4 + 1] = (argb shr 8).toByte()
        bytes[i * 4 + 2] = (argb shr 16).toByte()
        bytes[i * 4 + 3] = (argb ushr 24).toByte()
    }
    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    return Image.makeRaster(info, bytes, width * 4).toComposeImageBitmap()
        .also { it.asSkiaBitmap().setImmutable() }
}
