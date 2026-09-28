package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.util.COMBO_COUNT_MAX_SIZE
import at.smiech.cyanbat.util.COMBO_FONT_SIZE
import at.smiech.engine.EngineColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ComboHeatTest {

    @Test
    fun `without a streak it is a plain line of the HUD`() {
        assertFalse(ComboHeat.burning(1))
        assertEquals("Combo:", ComboHeat.title(1))
        assertEquals(EngineColors.CYAN, ComboHeat.color(1, 0f), "cyan like the rest of the HUD")
        assertEquals(0f, ComboHeat.flameStrength(1))
        assertEquals(COMBO_FONT_SIZE, ComboHeat.countSize(1))
    }

    @Test
    fun `the first step sets it alight`() {
        assertTrue(ComboHeat.burning(2))
        assertEquals("HOT", ComboHeat.title(2))
        assertTrue(ComboHeat.flameStrength(2) > 0f)
    }

    @Test
    fun `each rung up the ladder has a title of its own`() {
        val titles = (1..ComboHeat.SUPERNOVA).map(ComboHeat::title).distinct()
        assertEquals(
            listOf(
                "Combo:", "HOT", "BLAZING", "SCORCHING", "INFERNO", "HELLFIRE", "BLUE FLAME",
                "WHITE HOT", "SUPERNOVA",
            ),
            titles,
        )
    }

    /** The multiplier has no ceiling, so the ladder cannot run out under a long streak. */
    @Test
    fun `the top of the ladder holds however high the streak goes`() {
        for (multiplier in listOf(ComboHeat.SUPERNOVA, 100, 1_000, Int.MAX_VALUE)) {
            assertEquals("SUPERNOVA", ComboHeat.title(multiplier))
            assertEquals(1f, ComboHeat.flameStrength(multiplier))
            assertEquals(COMBO_COUNT_MAX_SIZE, ComboHeat.countSize(multiplier))
        }
    }

    @Test
    fun `every step up the ladder changes the color`() {
        for (multiplier in 1 until ComboHeat.SUPERNOVA) {
            assertNotEquals(
                ComboHeat.color(multiplier, 0f),
                ComboHeat.color(multiplier + 1, 0f),
                "x$multiplier and x${multiplier + 1} look alike",
            )
        }
    }

    @Test
    fun `past the ladder the colors keep cycling`() {
        for (multiplier in listOf(ComboHeat.SUPERNOVA, 500)) {
            val colors = (0 until 40).map { ComboHeat.color(multiplier, it * 0.1f) }.distinct()
            assertTrue(colors.size > 10, "x$multiplier only ever showed ${colors.size} colors")
        }
    }

    /**
     * The cycle runs through the ladder's own fires rather than round the color wheel, which would
     * pass through green, and a green fire reads as poison.
     */
    @Test
    fun `no fire is ever green`() {
        for (multiplier in listOf(2, 5, 12, 20, 30, ComboHeat.SUPERNOVA, 200)) {
            for (step in 0 until 200) {
                val seconds = step * 0.05f
                for (color in listOf(
                    ComboHeat.color(multiplier, seconds),
                    ComboHeat.emberColor(multiplier, seconds),
                    ComboHeat.coreColor(multiplier, seconds),
                )) {
                    val r = (color ushr 16) and 0xFF
                    val g = (color ushr 8) and 0xFF
                    val b = color and 0xFF
                    assertTrue(g <= maxOf(r, b), "x$multiplier at ${seconds}s is green: ${color.toUInt().toString(16)}")
                }
            }
        }
    }

    @Test
    fun `the count and the fire only ever grow with the streak`() {
        for (multiplier in 1 until 1_000) {
            assertTrue(ComboHeat.countSize(multiplier + 1) >= ComboHeat.countSize(multiplier))
            assertTrue(ComboHeat.flameStrength(multiplier + 1) >= ComboHeat.flameStrength(multiplier))
        }
    }
}
