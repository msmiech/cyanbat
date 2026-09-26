package at.smiech.engine.impl

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import at.smiech.engine.DisplayMode
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where the framebuffer is drawn in a view of any shape, and how a pointer on that view maps back
 * into it.
 *
 * Two shapes of answer, for the three [DisplayMode]s. [fitted] scales by one factor both ways, as
 * large as fits, and centers the result - leaving bars down the sides of a view wider than the
 * game, and above and below one taller than it. [stretched] covers the whole view, each axis scaled
 * on its own, which is what the game did before there was a choice: on a 20:9 phone that draws
 * every pixel about half as wide again as it is tall.
 *
 * Bars rather than more of the world, when the game keeps its shape, because the playfield is
 * *designed* at the framebuffer's size: enemies spawn at its right edge, a boss holds station a
 * fraction of the way across it, and a wave is paced by how long its enemies take to cross it. A
 * wider window onto the stage would give wide screens more warning of everything coming at them,
 * and how hard the game is would depend on the phone it is played on.
 *
 * Kept in whole view pixels, because that is where the image is drawn, and pointers are mapped back
 * through the same rectangle - so a touch lands on the framebuffer pixel that is under the finger.
 */
data class FrameFit(
    val frameBufferWidth: Int,
    val frameBufferHeight: Int,
    /** The drawn framebuffer's left edge, in view pixels. */
    val left: Int,
    /** The drawn framebuffer's top edge, in view pixels. */
    val top: Int,
    /** The drawn framebuffer's size, in view pixels. */
    val width: Int,
    val height: Int,
) {
    /**
     * A horizontal view position, in framebuffer pixels.
     *
     * Over a bar it comes back outside the framebuffer rather than clamped into it. Whatever reads
     * it already keeps the bat on screen, and a drag that wanders onto a bar should still move by as
     * much as the finger did - clamping would stall it at the edge until the finger came back.
     */
    fun toFrameBufferX(viewX: Float): Int = floor((viewX - left) * frameBufferWidth / width).toInt()

    /** A vertical view position, in framebuffer pixels. See [toFrameBufferX]. */
    fun toFrameBufferY(viewY: Float): Int = floor((viewY - top) * frameBufferHeight / height).toInt()

    companion object {
        /** Where [mode] draws the framebuffer in a view of [viewWidth] by [viewHeight] pixels. */
        fun of(
            mode: DisplayMode,
            frameBufferWidth: Int,
            frameBufferHeight: Int,
            viewWidth: Int,
            viewHeight: Int,
        ): FrameFit = when (mode) {
            DisplayMode.STRETCH -> stretched(frameBufferWidth, frameBufferHeight, viewWidth, viewHeight)
            DisplayMode.BLACK_BARS, DisplayMode.AMBIENT ->
                fitted(frameBufferWidth, frameBufferHeight, viewWidth, viewHeight)
        }

        /** The largest fit of the framebuffer into the view that keeps its shape, centered. */
        fun fitted(frameBufferWidth: Int, frameBufferHeight: Int, viewWidth: Int, viewHeight: Int): FrameFit {
            val scale = min(
                viewWidth.toFloat() / frameBufferWidth,
                viewHeight.toFloat() / frameBufferHeight,
            )
            // At least a pixel each way, so a view that has not been measured yet cannot leave the
            // pointer mapping dividing by zero.
            val width = (frameBufferWidth * scale).roundToInt().coerceAtLeast(1)
            val height = (frameBufferHeight * scale).roundToInt().coerceAtLeast(1)
            return FrameFit(
                frameBufferWidth,
                frameBufferHeight,
                left = (viewWidth - width) / 2,
                top = (viewHeight - height) / 2,
                width = width,
                height = height,
            )
        }

        /** The whole view, whatever its shape. */
        fun stretched(frameBufferWidth: Int, frameBufferHeight: Int, viewWidth: Int, viewHeight: Int): FrameFit =
            FrameFit(
                frameBufferWidth,
                frameBufferHeight,
                left = 0,
                top = 0,
                width = viewWidth.coerceAtLeast(1),
                height = viewHeight.coerceAtLeast(1),
            )
    }
}

/** The bars either side of the framebuffer, or above and below it, when nothing lights them. */
private val BAR_COLOR = Color.Black

/**
 * Draws [image], the framebuffer, where [fit] puts it, with the bars around it - black, or lit by
 * [ambient] when there is one.
 */
fun DrawScope.drawFrameBuffer(image: ImageBitmap, fit: FrameFit, ambient: AmbientBars? = null) {
    drawRect(BAR_COLOR)
    ambient?.draw(this, image, fit)
    drawImage(
        image = image,
        dstOffset = IntOffset(fit.left, fit.top),
        dstSize = IntSize(fit.width, fit.height),
    )
}
