package at.smiech.engine.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import at.smiech.engine.DisplayMode
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where the framebuffer is drawn in a view of any shape, and how a pointer on that view maps back
 * into it.
 *
 * The three [DisplayMode]s need two layouts. [fitted] scales by one factor, as large as fits, and
 * centers the result, leaving bars beside a wider view or above and below a taller one.
 * [stretched] covers the whole view, scaling each axis separately; on a 20:9 phone that draws every
 * pixel about half again as wide as it is tall.
 *
 * Bars rather than more of the world, because the playfield is designed at the framebuffer's size:
 * enemies spawn at its right edge, bosses hold station a fraction of the way across it, and waves
 * are paced by how long enemies take to cross it. A wider view would give wide screens more warning
 * and make difficulty depend on the device.
 *
 * Kept in whole view pixels, where the image is drawn, and pointers are mapped back through the
 * same rectangle, so a touch lands on the framebuffer pixel under the finger.
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
     * Over a bar the result lies outside the framebuffer rather than being clamped into it. Callers
     * already keep the bat on screen, and a drag that wanders onto a bar should still move as far
     * as the finger did; clamping would stall it at the edge.
     */
    fun toFrameBufferX(viewX: Float): Int = floor((viewX - left) * frameBufferWidth / width).toInt()

    /** A vertical view position, in framebuffer pixels. See [toFrameBufferX]. */
    fun toFrameBufferY(viewY: Float): Int =
        floor((viewY - top) * frameBufferHeight / height).toInt()

    companion object {
        /** Where [mode] draws the framebuffer in a view of [viewWidth] by [viewHeight] pixels. */
        fun of(
            mode: DisplayMode,
            frameBufferWidth: Int,
            frameBufferHeight: Int,
            viewWidth: Int,
            viewHeight: Int,
        ): FrameFit = when (mode) {
            DisplayMode.STRETCH -> stretched(
                frameBufferWidth,
                frameBufferHeight,
                viewWidth,
                viewHeight
            )

            DisplayMode.BLACK_BARS, DisplayMode.AMBIENT ->
                fitted(frameBufferWidth, frameBufferHeight, viewWidth, viewHeight)
        }

        /** The largest fit of the framebuffer into the view that keeps its shape, centered. */
        fun fitted(
            frameBufferWidth: Int,
            frameBufferHeight: Int,
            viewWidth: Int,
            viewHeight: Int
        ): FrameFit {
            val scale = min(
                viewWidth.toFloat() / frameBufferWidth,
                viewHeight.toFloat() / frameBufferHeight,
            )
            // At least a pixel each way, so an unmeasured view cannot make the
            // pointer mapping divide by zero.
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
        fun stretched(
            frameBufferWidth: Int,
            frameBufferHeight: Int,
            viewWidth: Int,
            viewHeight: Int
        ): FrameFit =
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

/** The bars either side of the frame, or above and below it, when nothing lights them. */
private val BAR_COLOR = Color.Black

/**
 * Draws the frame [graphics] last recorded where [fit] puts it, with the bars around it: black, or
 * lit by [ambient] when given.
 *
 * Scaled from frame pixels to the view in one transform, so everything lands on the frame's pixel
 * grid at any view size, and clipped to the frame, so a sprite half off the playfield stops at its
 * edge rather than spilling onto a bar.
 */
fun DrawScope.drawGameFrame(
    graphics: ComposeGraphics,
    fit: FrameFit,
    ambient: AmbientBars? = null
) {
    drawRect(BAR_COLOR)
    ambient?.draw(this, graphics, fit)
    val left = fit.left.toFloat()
    val top = fit.top.toFloat()
    clipRect(left, top, left + fit.width, top + fit.height) {
        withTransform({
            translate(left, top)
            scale(
                fit.width / fit.frameBufferWidth.toFloat(),
                fit.height / fit.frameBufferHeight.toFloat(),
                pivot = Offset.Zero,
            )
        }) {
            graphics.draw(this)
        }
    }
}
