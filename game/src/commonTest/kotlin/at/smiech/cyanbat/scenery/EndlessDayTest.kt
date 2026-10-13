package at.smiech.cyanbat.scenery

import at.smiech.cyanbat.scenery.EndlessDay.TimeOfDay
import at.smiech.engine.ecs.LightingSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The endless stage's day, which goes round and round. */
class EndlessDayTest {

    @Test
    fun `the day goes round every SECONDS and starts after sunrise`() {
        assertEquals(EndlessDay.START, EndlessDay.position(0f), 1e-5f)
        assertEquals(EndlessDay.position(10f), EndlessDay.position(10f + EndlessDay.SECONDS), 1e-4f)
        assertEquals(EndlessDay.position(10f), EndlessDay.position(10f + 7 * EndlessDay.SECONDS), 1e-3f)
        assertTrue(EndlessDay.sunUp(EndlessDay.position(0f)))
    }

    @Test
    fun `noon is lit and the dead of night is dark`() {
        assertEquals(0f, EndlessDay.darkness(0.25f))
        assertEquals(LightingSystem.DAYLIGHT, EndlessDay.ambient(0.25f))
        assertEquals(1f, EndlessDay.darkness(0.82f), 1e-3f)
        assertNotEquals(LightingSystem.DAYLIGHT, EndlessDay.ambient(0.82f))
        assertFalse(EndlessDay.sunUp(0.82f))
        assertTrue(EndlessDay.moonlight(0.82f) > 0.9f)
        assertTrue(EndlessDay.starlight(0.82f) > 0.9f)
        assertEquals(0f, EndlessDay.starlight(0.25f))
    }

    @Test
    fun `the dark falls and lifts gradually`() {
        var before = EndlessDay.darkness(0.55f)
        var position = 0.55f
        while (position < 0.75f) {
            position += 0.005f
            val now = EndlessDay.darkness(position)
            assertTrue(now >= before && now - before < 0.12f, "the dark jumped at $position")
            before = now
        }
    }

    @Test
    fun `the ground's palettes run noon gold sunset night and dawn and wrap round`() {
        assertEquals(TimeOfDay.NOON.ordinal, EndlessDay.rowBelow(0.3f))
        assertEquals(0f, EndlessDay.rowBlend(0.3f))
        assertEquals(TimeOfDay.NIGHT.ordinal, EndlessDay.rowBelow(0.8f))
        assertEquals(0f, EndlessDay.rowBlend(0.8f))
        // The night fades into the dawn as the clock comes round.
        assertEquals(TimeOfDay.NIGHT.ordinal, EndlessDay.rowBelow(0.97f))
        assertEquals(TimeOfDay.DAWN.ordinal, EndlessDay.rowAbove(0.97f))
        assertTrue(EndlessDay.rowBlend(0.97f) in 0.01f..0.99f)
        assertTrue(EndlessDay.rowBlend(0.9999f) > 0.99f)
        assertEquals(TimeOfDay.DAWN.ordinal, EndlessDay.rowBelow(0f))
    }

    @Test
    fun `the sky's colors come round without a jump`() {
        for (height in listOf(0f, 0.5f, 1f)) {
            val late = EndlessDay.skyColor(0.9999f, height)
            val early = EndlessDay.skyColor(0f, height)
            for (shift in listOf(16, 8, 0)) {
                val a = (late shr shift) and 0xFF
                val b = (early shr shift) and 0xFF
                assertTrue(kotlin.math.abs(a - b) <= 2, "the sky jumps at midnight at height $height")
            }
        }
    }

    @Test
    fun `the sun rises on the right and sets on the left`() {
        assertTrue(EndlessDay.sunX(0.01f) > 500f)
        assertTrue(EndlessDay.sunX(EndlessDay.SUNSET - 0.01f) < 100f)
        assertTrue(EndlessDay.sunY(0.32f, 252) < EndlessDay.sunY(0.02f, 252))
    }
}
