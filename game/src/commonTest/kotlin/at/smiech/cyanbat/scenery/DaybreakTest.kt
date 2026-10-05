package at.smiech.cyanbat.scenery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun red(color: Int) = (color shr 16) and 0xFF
private fun green(color: Int) = (color shr 8) and 0xFF
private fun blue(color: Int) = color and 0xFF
private fun brightness(color: Int) = red(color) + green(color) + blue(color)

/** The lagoon's day, read off the stage clock: night at the first second, noon on the boss's. */
class DaybreakTest {

    // region the ground

    @Test
    fun `the ground is in each keyframe's own palette exactly at that keyframe`() {
        Daybreak.KEYFRAMES.forEachIndexed { index, at ->
            assertEquals(index, Daybreak.keyframeBelow(at))
            assertEquals(
                0f,
                Daybreak.keyframeBlend(at),
                "keyframe $index was blended at its own hour"
            )
        }
    }

    /** Four, as the desert has, so the scenery's sheets stack the same way. */
    @Test
    fun `it is drawn in as many lights as the desert`() {
        assertEquals(Daylight.KEYFRAMES.size, Daybreak.KEYFRAMES.size)
    }

    // endregion

    // region the sky

    @Test
    fun `night is dark overhead and noon is bright and cyan`() {
        val night = Daybreak.skyColor(0f, 0f)
        assertTrue(brightness(night) < 120, "the night sky overhead is too bright")

        val noon = Daybreak.skyColor(1f, 0f)
        assertTrue(brightness(noon) > 500, "the noon sky overhead is not bright")
        assertTrue(
            blue(noon) > red(noon) + 100 && green(noon) > red(noon) + 100,
            "the noon sky overhead is not cyan"
        )
    }

    /** The dawn comes up from the horizon: pink there while the sky overhead is still dark. */
    @Test
    fun `the dawn comes up pink along the horizon first`() {
        val dawn = 0.3f
        val horizon = Daybreak.skyColor(dawn, 1f)
        val overhead = Daybreak.skyColor(dawn, 0f)
        assertTrue(red(horizon) > blue(horizon), "the dawn's horizon is not pink")
        assertTrue(
            brightness(horizon) > brightness(overhead) + 150,
            "the dawn has not come up from the horizon"
        )
    }

    @Test
    fun `the sky only ever brightens overhead as the day comes up`() {
        var last = -1
        for (step in 0..100) {
            val overhead = brightness(Daybreak.skyColor(step / 100f, 0f))
            assertTrue(overhead >= last, "the sky darkened overhead at ${step / 100f}")
            last = overhead
        }
    }

    // endregion

    // region the sun

    @Test
    fun `the sun comes up out of the sea on the right and climbs`() {
        assertFalse(Daybreak.sunUp(0.2f), "the sun is up in the night")
        assertTrue(Daybreak.sunUp(Daybreak.SUNRISE))
        assertTrue(Daybreak.sunX(Daybreak.SUNRISE) > 400f, "it rose behind the bat")
        assertTrue(
            Daybreak.sunY(Daybreak.SUNRISE) - Daybreak.sunRadius(Daybreak.SUNRISE) > 220f,
            "it rose in the sky, not out of the sea"
        )
        var last = Float.MAX_VALUE
        for (step in 36..100) {
            val y = Daybreak.sunY(step / 100f)
            assertTrue(y <= last, "the sun went down at ${step / 100f}")
            last = y
        }
        assertTrue(Daybreak.sunY(1f) < 80f, "the sun is not high at noon")
    }

    /** Cut into bands by the haze as it rises, clear of it at noon - and the desert's never is. */
    @Test
    fun `the sun is banded as it rises and clear at noon`() {
        assertEquals(1f, Daybreak.sunBands(Daybreak.SUNUP))
        assertEquals(0f, Daybreak.sunBands(1f))
        assertEquals(0f, Daylight.sunBands(Daylight.SUNDOWN))
    }

    @Test
    fun `the sun lays its path on the water while it is low and not at noon`() {
        assertTrue(Daybreak.sunGlitter(Daybreak.SUNUP) > 0.5f, "no path under the rising sun")
        assertEquals(0f, Daybreak.sunGlitter(0.95f))
        assertEquals(
            0f,
            Daylight.sunGlitter(Daylight.SUNDOWN),
            "the desert has no water to lay it on"
        )
    }

    // endregion

    // region the night

    @Test
    fun `every star is out at the start and none by sunrise`() {
        assertEquals(1f, Daybreak.starlight(0f))
        assertEquals(0f, Daybreak.starlight(Daybreak.SUNRISE))
    }

    /** It goes down behind the bat, on the left, as the dawn comes up ahead. */
    @Test
    fun `the moon sets on the left before the sun is up`() {
        assertEquals(1f, Daybreak.moonlight(0f))
        assertEquals(0f, Daybreak.moonlight(Daybreak.SUNRISE))
        assertTrue(Daybreak.moonX(0f) < 200f, "the moon is not behind the bat")
        assertTrue(Daybreak.moonY(0.3f) > Daybreak.moonY(0f), "the moon is not going down")
    }

    // endregion
}
