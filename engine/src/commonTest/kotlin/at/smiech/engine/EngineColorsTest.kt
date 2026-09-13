package at.smiech.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineColorsTest {

    @Test
    fun `lerp returns the ends unchanged`() {
        assertEquals(EngineColors.RED, EngineColors.lerp(EngineColors.RED, EngineColors.CYAN, 0f))
        assertEquals(EngineColors.CYAN, EngineColors.lerp(EngineColors.RED, EngineColors.CYAN, 1f))
    }

    @Test
    fun `lerp blends every channel`() {
        val halfway = EngineColors.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f)

        assertEquals(0xFF808080.toInt(), halfway)
    }

    /** Alpha rides along with the rest, so a ramp can fade out as well as change hue. */
    @Test
    fun `lerp interpolates alpha too`() {
        val halfway = EngineColors.lerp(0x00FF0000, 0xFFFF0000.toInt(), 0.5f)

        assertEquals(0x80, (halfway ushr 24) and 0xFF)
        assertEquals(0xFF, (halfway ushr 16) and 0xFF)
    }

    /** Out of range is clamped rather than extrapolated: a ramp cannot overshoot its own ends. */
    @Test
    fun `lerp clamps out of range amounts`() {
        assertEquals(EngineColors.RED, EngineColors.lerp(EngineColors.RED, EngineColors.CYAN, -1f))
        assertEquals(EngineColors.CYAN, EngineColors.lerp(EngineColors.RED, EngineColors.CYAN, 2f))
    }
}
