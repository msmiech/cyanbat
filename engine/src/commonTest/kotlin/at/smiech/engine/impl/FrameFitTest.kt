package at.smiech.engine.impl

import at.smiech.engine.DisplayMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameFitTest {

    private fun fitInto(viewWidth: Int, viewHeight: Int) = FrameFit.fitted(480, 320, viewWidth, viewHeight)

    /** A 20:9 phone in landscape, which is what the stretch used to squash worst. */
    @Test
    fun `a wide phone gets bars down the sides`() {
        assertEquals(FrameFit(480, 320, left = 390, top = 0, width = 1620, height = 1080), fitInto(2400, 1080))
    }

    /** The desktop's default window is 4:3, taller than the game. */
    @Test
    fun `a view taller than the game gets bars above and below`() {
        assertEquals(FrameFit(480, 320, left = 0, top = 50, width = 1200, height = 800), fitInto(1200, 900))
    }

    @Test
    fun `a view of the framebuffer's own shape is filled with no bars`() {
        assertEquals(FrameFit(480, 320, left = 0, top = 0, width = 1440, height = 960), fitInto(1440, 960))
    }

    /**
     * The whole point: however odd the view, a framebuffer pixel comes out as wide as it is tall -
     * give or take the one view pixel lost to rounding the drawn size to whole pixels.
     */
    @Test
    fun `a framebuffer pixel is scaled the same both ways`() {
        for ((viewWidth, viewHeight) in listOf(2400 to 1080, 2340 to 1080, 1920 to 1200, 1280 to 800, 1000 to 1000, 777 to 333)) {
            val fit = fitInto(viewWidth, viewHeight)
            val heightAtWidthsScale = fit.width * 320f / 480f
            assertTrue(
                abs(heightAtWidthsScale - fit.height) <= 1f,
                "${viewWidth}x$viewHeight drew the framebuffer ${fit.width}x${fit.height}, which is not 3:2",
            )
            assertTrue(fit.width <= viewWidth && fit.height <= viewHeight, "${viewWidth}x$viewHeight overflowed")
            assertTrue(
                fit.width == viewWidth || fit.height == viewHeight,
                "${viewWidth}x$viewHeight left room on every side - the fit should be as large as it can be",
            )
        }
    }

    /** Pointers go back through the same rectangle the image is drawn into. */
    @Test
    fun `a touch lands on the framebuffer pixel under it`() {
        val fit = fitInto(2400, 1080)

        assertEquals(0, fit.toFrameBufferX(390f))
        assertEquals(0, fit.toFrameBufferY(0f))
        assertEquals(479, fit.toFrameBufferX(2009.9f))
        assertEquals(319, fit.toFrameBufferY(1079.9f))
        assertEquals(240, fit.toFrameBufferX(1200f))
        assertEquals(160, fit.toFrameBufferY(540f))
    }

    /**
     * Over a bar a touch comes back outside the framebuffer, not pinned to its edge, so a drag that
     * wanders onto a bar keeps moving by as much as the finger did. The bat is kept on screen by
     * what reads the touch, not by this.
     */
    @Test
    fun `a touch over a bar lands outside the framebuffer`() {
        val fit = fitInto(2400, 1080)

        assertTrue(fit.toFrameBufferX(0f) < 0, "the left bar")
        assertTrue(fit.toFrameBufferX(2399f) >= 480, "the right bar")
        assertEquals(-1, fit.toFrameBufferX(389f), "just left of the framebuffer is its column -1, not 0")
    }

    @Test
    fun `a view that has not been measured yet does not divide by zero`() {
        for (fit in listOf(fitInto(0, 0), FrameFit.stretched(480, 320, 0, 0))) {
            assertTrue(fit.width >= 1 && fit.height >= 1)
            assertEquals(0, fit.toFrameBufferX(0f))
            assertEquals(0, fit.toFrameBufferY(0f))
        }
    }

    /** Stretch is the old behavior, kept as a choice: the whole view, each axis scaled on its own. */
    @Test
    fun `stretch covers the whole view and maps each axis on its own`() {
        val fit = FrameFit.of(DisplayMode.STRETCH, 480, 320, 2400, 1080)

        assertEquals(FrameFit(480, 320, left = 0, top = 0, width = 2400, height = 1080), fit)
        assertEquals(1, fit.toFrameBufferX(5f), "five view pixels to a framebuffer pixel across")
        assertEquals(0, fit.toFrameBufferY(3f), "but only 3.375 down")
        assertEquals(1, fit.toFrameBufferY(3.5f))
        assertEquals(240, fit.toFrameBufferX(1200f))
        assertEquals(160, fit.toFrameBufferY(540f))
    }

    /** The two bar modes differ only in what the bars are filled with, never in where the game goes. */
    @Test
    fun `both bar modes put the game in the same place`() {
        val fitted = FrameFit.fitted(480, 320, 2400, 1080)

        assertEquals(fitted, FrameFit.of(DisplayMode.BLACK_BARS, 480, 320, 2400, 1080))
        assertEquals(fitted, FrameFit.of(DisplayMode.AMBIENT, 480, 320, 2400, 1080))
    }
}
