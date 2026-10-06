package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.util.MAX_EXTRA_SHOTS
import at.smiech.cyanbat.util.SPREAD_ANGLE_DEGREES
import kotlin.test.Test
import kotlin.test.assertEquals

/** The headings of a fan of shots; see [spreadAngles]. */
class SpreadShotTest {

    @Test
    fun `a single shot flies straight`() {
        assertEquals(listOf(0f), spreadAngles(1))
    }

    @Test
    fun `an odd fan keeps a shot down the middle`() {
        assertEquals(listOf(-STEP, 0f, STEP), spreadAngles(3))
        assertEquals(listOf(-2 * STEP, -STEP, 0f, STEP, 2 * STEP), spreadAngles(5))
    }

    @Test
    fun `an even fan straddles the line half a step either side`() {
        assertEquals(listOf(-STEP / 2, STEP / 2), spreadAngles(2))
        assertEquals(listOf(-1.5f * STEP, -STEP / 2, STEP / 2, 1.5f * STEP), spreadAngles(4))
    }

    @Test
    fun `every fan the bat can fire is balanced and evenly stepped`() {
        for (shots in 1..1 + MAX_EXTRA_SHOTS) {
            val angles = spreadAngles(shots)
            assertEquals(shots, angles.size)
            assertEquals(0f, angles.sum(), "$shots shots")
            angles.zipWithNext { above, below ->
                assertEquals(STEP, below - above, "$shots shots")
            }
        }
    }

    private companion object {
        const val STEP = SPREAD_ANGLE_DEGREES
    }
}
