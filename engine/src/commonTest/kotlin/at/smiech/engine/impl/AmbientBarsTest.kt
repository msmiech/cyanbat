package at.smiech.engine.impl

import at.smiech.engine.DisplayMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmbientBarsTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val green = 0xFF00FF00.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun assertColor(r: Float, g: Float, b: Float, rgb: FloatArray, band: Int) {
        val actual = Triple(rgb[band * 3], rgb[band * 3 + 1], rgb[band * 3 + 2])
        assertTrue(
            abs(actual.first - r) < 0.001f && abs(actual.second - g) < 0.001f && abs(actual.third - b) < 0.001f,
            "band $band was $actual, expected ($r, $g, $b)",
        )
    }

    /** A side edge is read as a column: its bands run down the screen. */
    @Test
    fun `bands run down a side edge`() {
        // Two columns wide, four rows tall: red on top, blue below.
        val pixels = intArrayOf(red, red, red, red, blue, blue, blue, blue)
        val rgb = FloatArray(2 * 3)

        averageBands(pixels, width = 2, height = 4, bands = 2, alongY = true, into = rgb)

        assertColor(1f, 0f, 0f, rgb, band = 0)
        assertColor(0f, 0f, 1f, rgb, band = 1)
    }

    /** The top and bottom edges are read as rows: their bands run across the screen. */
    @Test
    fun `bands run across a top or bottom edge`() {
        // Four columns wide, two rows tall: green on the left, white on the right.
        val pixels = intArrayOf(green, green, white, white, green, green, white, white)
        val rgb = FloatArray(2 * 3)

        averageBands(pixels, width = 4, height = 2, bands = 2, alongY = false, into = rgb)

        assertColor(0f, 1f, 0f, rgb, band = 0)
        assertColor(1f, 1f, 1f, rgb, band = 1)
    }

    /** A band is an average of what is in it, so half red and half blue reads as purple. */
    @Test
    fun `a band averages what it covers`() {
        val pixels = intArrayOf(red, blue)
        val rgb = FloatArray(3)

        averageBands(pixels, width = 2, height = 1, bands = 1, alongY = true, into = rgb)

        assertColor(0.5f, 0f, 0.5f, rgb, band = 0)
    }

    /** More bands than the edge has rows still gives every band a color, rather than dividing by zero. */
    @Test
    fun `more bands than rows still fills every band`() {
        val pixels = IntArray(3) { white }
        val rgb = FloatArray(10 * 3) { -1f }

        averageBands(pixels, width = 1, height = 3, bands = 10, alongY = true, into = rgb)

        for (band in 0 until 10) assertColor(1f, 1f, 1f, rgb, band)
    }

    /**
     * The bars ease toward a new reading at a rate set in seconds, not in frames, so they settle as
     * fast on a 120Hz phone as on a 60Hz one: two short frames land where one long one does.
     */
    @Test
    fun `easing does not depend on the frame rate`() {
        val oneLongFrame = AmbientBars.easeFor(1f / 30f)
        val short = AmbientBars.easeFor(1f / 60f)
        val twoShortFrames = 1f - (1f - short) * (1f - short)

        assertTrue(abs(oneLongFrame - twoShortFrames) < 0.0001f, "$oneLongFrame vs $twoShortFrames")
    }

    @Test
    fun `easing covers most of the way in its time and never overshoots`() {
        assertEquals(0f, AmbientBars.easeFor(0f))
        assertTrue(abs(AmbientBars.easeFor(AmbientBars.EASE_SECONDS) - 0.632f) < 0.01f)
        assertTrue(AmbientBars.easeFor(10f) <= 1f)
    }

    /** A setting stored by a newer build, or mangled, falls back rather than failing to start. */
    @Test
    fun `an unknown stored display mode reads as the default`() {
        assertEquals(DisplayMode.STRETCH, DisplayMode.fromName("STRETCH"))
        assertEquals(DisplayMode.DEFAULT, DisplayMode.fromName("HOLOGRAM"))
        assertEquals(DisplayMode.DEFAULT, DisplayMode.fromName(null))
    }
}
