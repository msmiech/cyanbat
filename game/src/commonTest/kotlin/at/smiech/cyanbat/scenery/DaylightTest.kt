package at.smiech.cyanbat.scenery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Brightness of a packed color, as the plain sum of its channels. */
private fun brightness(color: Int): Int =
    ((color shr 16) and 0xFF) + ((color shr 8) and 0xFF) + (color and 0xFF)

private fun red(color: Int) = (color shr 16) and 0xFF
private fun green(color: Int) = (color shr 8) and 0xFF
private fun blue(color: Int) = color and 0xFF

/** The desert's day, read off the stage clock: noon at the first second, night on the boss's. */
class DaylightTest {

    @Test
    fun `the day runs from noon at the start to night when the boss arrives and holds there`() {
        assertEquals(0f, Daylight.position(0f, 300f))
        assertEquals(0.5f, Daylight.position(150f, 300f))
        assertEquals(1f, Daylight.position(300f, 300f))
        assertEquals(1f, Daylight.position(420f, 300f), "the night should hold through the boss fight")
    }

    // region the ground

    @Test
    fun `the ground is in each keyframe's own palette exactly at that keyframe`() {
        Daylight.KEYFRAMES.forEachIndexed { index, at ->
            assertEquals(index, Daylight.keyframeBelow(at))
            assertEquals(0f, Daylight.keyframeBlend(at), "keyframe $index was blended at its own hour")
        }
    }

    @Test
    fun `between two keyframes the ground fades from the one to the next`() {
        val between = (Daylight.KEYFRAMES[1] + Daylight.KEYFRAMES[2]) / 2f

        assertEquals(1, Daylight.keyframeBelow(between))
        assertEquals(2, Daylight.keyframeAbove(between))
        assertEquals(0.5f, Daylight.keyframeBlend(between), 0.001f)
    }

    @Test
    fun `past the last keyframe the ground stays at night`() {
        val last = Daylight.KEYFRAMES.lastIndex

        assertEquals(last, Daylight.keyframeBelow(1f))
        assertEquals(last, Daylight.keyframeAbove(1f))
        assertEquals(0f, Daylight.keyframeBlend(1f))
    }

    // endregion

    // region the sky

    /** What the stage is about: "yellowish bright" at the start, "dark black and purple" at the end. */
    @Test
    fun `noon is bright and yellow`() {
        for (height in floatArrayOf(0f, 0.5f, 1f)) {
            val sky = Daylight.skyColor(0f, height)
            assertTrue(brightness(sky) > 600, "noon at $height is not bright: ${sky.toString(16)}")
            assertTrue(red(sky) > blue(sky) + 30 && green(sky) > blue(sky) + 20, "noon at $height is not yellow")
        }
    }

    @Test
    fun `night is black overhead and purple along the horizon`() {
        val overhead = Daylight.skyColor(1f, 0f)
        val horizon = Daylight.skyColor(1f, 1f)

        assertTrue(brightness(overhead) < 50, "the night sky overhead is not black: ${overhead.toString(16)}")
        assertTrue(blue(horizon) > green(horizon) && red(horizon) > green(horizon), "the horizon at night is not purple")
    }

    @Test
    fun `the sky only ever darkens overhead as the day goes on`() {
        var previous = Int.MAX_VALUE
        for (step in 0..100) {
            val overhead = brightness(Daylight.skyColor(step / 100f, 0f))
            assertTrue(overhead <= previous, "the sky got lighter overhead at ${step / 100f}")
            previous = overhead
        }
    }

    // endregion

    // region the sun, the moon and the stars

    @Test
    fun `the sun goes down steadily and is gone before dusk`() {
        var previous = Float.NEGATIVE_INFINITY
        for (step in 0..76) {
            val y = Daylight.sunY(step / 100f)
            assertTrue(y > previous, "the sun climbed at ${step / 100f}")
            previous = y
        }
        assertTrue(Daylight.SUNSET < 0.8f, "the sun is still up for the last wave")
        assertTrue(Daylight.sunY(Daylight.SUNSET) - Daylight.sunRadius(Daylight.SUNSET) > 244f, "the sun is not below the dunes when it stops being drawn")
    }

    /** The far dunes' crests run along 230 to 244 of the frame. */
    @Test
    fun `at sundown the sun sits on the far dunes`() {
        val center = Daylight.sunY(Daylight.SUNDOWN)
        val radius = Daylight.sunRadius(Daylight.SUNDOWN)

        assertTrue(center in 218f..236f, "the sun is not on the horizon at sundown: its center is at $center")
        assertTrue(center - radius < 214f, "none of the sun shows above the dunes at sundown")
    }

    @Test
    fun `the sun reddens as it sets`() {
        val noon = Daylight.sunColor(0f)
        val setting = Daylight.sunColor(0.72f)

        assertTrue(blue(setting) < blue(noon) - 100, "the setting sun is not warmer than the noon one")
    }

    @Test
    fun `no star or moon shows by day and all of them do at night`() {
        assertEquals(0f, Daylight.starlight(0.5f))
        assertEquals(0f, Daylight.moonlight(0.6f))
        assertEquals(1f, Daylight.starlight(1f))
        assertEquals(1f, Daylight.moonlight(1f))
    }

    @Test
    fun `the stars come out before night falls rather than on the boss's arrival`() {
        assertTrue(Daylight.starlight(0.85f) > 0.3f, "dusk has no stars in it")
    }

    // endregion
}
