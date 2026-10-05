package at.smiech.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A frame's light as the lighting hands it to a [Graphics]: its lights' rings, and their shadows. */
class LightingTest {

    @Test
    fun `a light holds full strength out to its full reach and is gone at its edge`() {
        assertEquals(1f, Lighting.falloff(0f))
        assertEquals(1f, Lighting.falloff(Lighting.FULL_REACH))
        assertEquals(0f, Lighting.falloff(1f))
        val steps = (0..100).map { Lighting.falloff(it / 100f) }
        assertTrue(
            steps.zipWithNext().all { (near, far) -> far <= near },
            "it brightens somewhere going out"
        )
    }

    /** Pixel-art light: bands of brightness, each a ring the next one in is brighter than. */
    @Test
    fun `a light's rings brighten inward out to its radius and no further`() {
        for (radius in listOf(8, 50, 160)) {
            val rings =
                Lighting.rings(radius).toList().chunked(2) { (ring, level) -> ring to level }
            assertTrue(rings.isNotEmpty(), "a light of $radius has no rings")
            assertTrue(
                rings.first().first <= radius,
                "a light of $radius reaches ${rings.first().first}"
            )
            assertTrue(
                rings.zipWithNext().all { (outer, inner) -> inner.first <= outer.first },
                "rings out of order at $radius"
            )
            assertTrue(
                rings.zipWithNext().all { (outer, inner) -> inner.second > outer.second },
                "brightness out of order at $radius"
            )
            assertEquals(255, rings.last().second, "a light of $radius is not full at its heart")
        }
    }

    @Test
    fun `a frame's light starts over empty and says it has changed`() {
        val lighting = Lighting()
        lighting.begin(EngineColors.BLACK, 0f)
        lighting.add(10, 10, 20, EngineColors.WHITE, 1f)
        val first = lighting.version

        lighting.begin(EngineColors.WHITE, 0.5f)

        assertEquals(0, lighting.count)
        assertEquals(0, lighting.glintCount)
        assertEquals(EngineColors.WHITE, lighting.ambient)
        assertTrue(lighting.version != first)
    }

    @Test
    fun `fewer than three points make no shadow`() {
        val light = Lighting().add(0, 0, 10, EngineColors.WHITE, 1f)
        light.addShadowPoint(1f, 1f)
        light.addShadowPoint(5f, 1f)
        light.closeShadow()
        assertEquals(0, light.shadowCount)

        square(light, 0f, 0f, 4f)
        assertEquals(1, light.shadowCount)
        assertEquals(8, light.shadowEnd(0))
    }

    @Test
    fun `a point is in shadow inside any shadow on the light but the one it is spared`() {
        val light = Lighting().add(0, 0, 10, EngineColors.WHITE, 1f)
        square(light, 0f, 0f, 4f)
        square(light, 10f, 0f, 4f)

        assertTrue(light.inShadow(2f, 2f))
        assertTrue(light.inShadow(12f, 2f))
        assertFalse(light.inShadow(7f, 2f))
        assertFalse(light.inShadow(2f, 2f, except = 0))
        assertTrue(light.inShadow(12f, 2f, except = 0))
    }

    private fun square(light: Lighting.Light, left: Float, top: Float, side: Float) {
        light.addShadowPoint(left, top)
        light.addShadowPoint(left + side, top)
        light.addShadowPoint(left + side, top + side)
        light.addShadowPoint(left, top + side)
        light.closeShadow()
    }
}
