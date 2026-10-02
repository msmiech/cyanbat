package at.smiech.engine.ecs

import at.smiech.engine.Lighting
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The outlines shadows are cast in, and the shadows cast from them: plain geometry, worked out the
 * same on every platform, and the place to pin it down.
 */
class ShadowTest {

    /** A [width] by [height] picture, opaque wherever [opaque] says and clear everywhere else. */
    private fun picture(width: Int, height: Int, opaque: (x: Int, y: Int) -> Boolean) =
        IntArray(width * height) { if (opaque(it % width, it / width)) OPAQUE_GRAY else 0 }

    /** The corners of an outline, as pairs, in the order it winds through them. */
    private fun corners(outline: FloatArray): List<Pair<Float, Float>> =
        outline.toList().chunked(2) { (x, y) -> x to y }

    /** Twice the signed area of the polygon in [points]: positive one way round, negative the other. */
    private fun winding(points: FloatArray, from: Int = 0, until: Int = points.size): Float {
        var sum = 0f
        var previous = until - 2
        for (point in from until until step 2) {
            sum += points[previous] * points[point + 1] - points[point] * points[previous + 1]
            previous = point
        }
        return sum
    }

    @Test
    fun `a solid block's outline is its four corners`() {
        val outline = assertNotNull(ConvexHull.ofOpaque(picture(6, 4) { _, _ -> true }, 6, 4))

        assertEquals(setOf(0f to 0f, 6f to 0f, 6f to 4f, 0f to 4f), corners(outline).toSet())
        assertEquals(4, corners(outline).size)
    }

    /** A plus sign's hull cuts across its four inner corners, which no shadow could rest on. */
    @Test
    fun `an outline is the hull of the picture without the corners a hull cannot rest on`() {
        val plus = picture(9, 9) { x, y -> x in 3..5 || y in 3..5 }
        val outline = assertNotNull(ConvexHull.ofOpaque(plus, 9, 9))

        assertEquals(
            setOf(3f to 0f, 6f to 0f, 9f to 3f, 9f to 6f, 6f to 9f, 3f to 9f, 0f to 6f, 0f to 3f),
            corners(outline).toSet(),
        )
    }

    /** The art's antialiased fringe is not the art: a shadow is cast from what reads as solid. */
    @Test
    fun `pixels less than half opaque are air and a frame of nothing else has no outline`() {
        val faint = IntArray(16) { 0x60FFFFFF }
        assertNull(ConvexHull.ofOpaque(faint, 4, 4))

        val dot = faint.copyOf().also { it[5] = OPAQUE_GRAY }
        assertEquals(setOf(1f to 1f, 2f to 1f, 2f to 2f, 1f to 2f), corners(ConvexHull.ofOpaque(dot, 4, 4)!!).toSet())
    }

    /** Which way a shadow is thrown off an outline depends on which way round the outline winds. */
    @Test
    fun `every outline winds the same way round`() {
        val shapes = listOf(
            picture(6, 4) { _, _ -> true },
            picture(9, 9) { x, y -> x in 3..5 || y in 3..5 },
            picture(12, 12) { x, y -> (x - 6) * (x - 6) + (y - 6) * (y - 6) <= 25 },
            picture(10, 10) { x, y -> x >= y },
        )
        for (shape in shapes) {
            val side = sqrt(shape.size.toFloat()).toInt()
            assertTrue(winding(ConvexHull.ofOpaque(shape, side, shape.size / side)!!) > 0f)
        }
    }

    /**
     * A light at (0, 15) and a block from (10, 10) to (20, 20): the shadow is the wedge of the light's
     * rays the block stops, beyond the block - not the block itself, and nothing between it and the
     * light, or either side of the wedge.
     */
    @Test
    fun `a block throws its shadow away from the light and not over itself`() {
        val light = cast(BLOCK, 0.5f, 15f)

        assertTrue(light.inShadow(30f, 15f), "straight behind it")
        assertTrue(light.inShadow(25f, 21f), "behind it, below its middle")
        assertFalse(light.inShadow(15f, 15f), "on the block itself")
        assertFalse(light.inShadow(5f, 15f), "between it and the light")
        assertFalse(light.inShadow(30f, -10f), "beside the wedge")
        assertFalse(light.inShadow(30f, 40f), "beside the wedge, on the other side")
    }

    /** The shadow reaches the corners of the light's square, where its disc ends, and further. */
    @Test
    fun `a shadow runs past everything the light reaches`() {
        val light = cast(BLOCK, 0.5f, 15f, reach = 50f)

        for (distance in 21..71) assertTrue(light.inShadow(distance.toFloat(), 15f), "$distance pixels along")
        assertTrue(light.inShadow(50f, 20f), "at the far edge of the light's square")
    }

    @Test
    fun `a light inside an outline throws no shadow from it`() {
        val light = Lighting().add(15, 15, 40, 0, 1f)

        assertFalse(Shadow.cast(BLOCK, 0, BLOCK.size, 15.5f, 15.5f, 41f, light))
        assertEquals(0, light.shadowCount)
    }

    /**
     * Every shadow is wound the other way to its outline, wherever the light is, so that any number
     * of them filled as one path, non-zero, cover their union rather than cancelling where they overlap.
     */
    @Test
    fun `every shadow winds the same way round`() {
        val lights = listOf(0f to 15f, 15f to 0f, 35f to 15f, 15f to 40f, -5f to -5f, 30f to 30f, 21f to 15f)
        for ((x, y) in lights) {
            val light = cast(BLOCK, x, y)
            assertEquals(1, light.shadowCount, "a light at $x, $y")
            assertTrue(winding(light.shadowPoints, 0, light.shadowEnd(0)) < 0f, "the shadow of a light at $x, $y")
        }
    }

    /** One shadow of [outline] from a light at ([x], [y]) reaching [reach]. */
    private fun cast(outline: FloatArray, x: Float, y: Float, reach: Float = 60f): Lighting.Light {
        val light = Lighting().add(x.toInt(), y.toInt(), reach.toInt(), 0, 1f)
        assertTrue(Shadow.cast(outline, 0, outline.size, x, y, reach, light))
        return light
    }

    private companion object {
        val OPAQUE_GRAY = 0xFF808080.toInt()

        /** A ten-pixel block from (10, 10) to (20, 20), wound the way [ConvexHull] winds. */
        val BLOCK = floatArrayOf(10f, 10f, 20f, 10f, 20f, 20f, 10f, 20f)
    }
}
