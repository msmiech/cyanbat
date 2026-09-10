package at.smiech.engine.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RectTest {

    @Test
    fun `fromLTWH derives the far edges`() {
        val r = Rect.fromLTWH(10f, 20f, 45f, 40f)
        assertEquals(55f, r.right)
        assertEquals(60f, r.bottom)
        assertEquals(45f, r.width)
        assertEquals(40f, r.height)
    }

    @Test
    fun `centre is the midpoint`() {
        val r = Rect.fromLTRB(10f, 20f, 30f, 60f)
        assertEquals(20f, r.centerX)
        assertEquals(40f, r.centerY)
    }

    @Test
    fun `overlapping rects intersect and touching ones do not`() {
        val a = Rect.fromLTWH(0f, 0f, 10f, 10f)
        assertTrue(a.intersects(Rect.fromLTWH(5f, 5f, 10f, 10f)))
        // Edge-to-edge is a miss: the comparison is strict, so a sprite exactly
        // abutting another does not count as a hit.
        assertFalse(a.intersects(Rect.fromLTWH(10f, 0f, 10f, 10f)))
        assertFalse(a.intersects(Rect.fromLTWH(20f, 20f, 5f, 5f)))
    }

    @Test
    fun `intersects is symmetric`() {
        val a = Rect.fromLTWH(0f, 0f, 10f, 10f)
        val b = Rect.fromLTWH(5f, 5f, 10f, 10f)
        assertEquals(a.intersects(b), b.intersects(a))
    }

    /**
     * Unlike [Rect.intersects], containment includes the border: a grab box is there to be
     * generous, and a touch landing exactly on its edge should count.
     */
    @Test
    fun `contains includes the edges and excludes points beyond them`() {
        val r = Rect.fromLTRB(10f, 20f, 30f, 40f)
        assertTrue(r.contains(20f, 30f))
        assertTrue(r.contains(10f, 20f))
        assertTrue(r.contains(30f, 40f))
        assertFalse(r.contains(9.9f, 30f))
        assertFalse(r.contains(20f, 40.1f))
    }

    @Test
    fun `offset moves all edges and preserves size`() {
        val r = Rect.fromLTWH(10f, 20f, 30f, 40f).offset(-2.5f, 1.5f)
        assertEquals(7.5f, r.left)
        assertEquals(21.5f, r.top)
        assertEquals(30f, r.width)
        assertEquals(40f, r.height)
    }

    /** Collision shrinks hitboxes with a negative inflate; check it really shrinks. */
    @Test
    fun `negative inflate shrinks the rect on every side`() {
        val r = Rect.fromLTWH(0f, 0f, 20f, 20f).inflate(-5f)
        assertEquals(5f, r.left)
        assertEquals(5f, r.top)
        assertEquals(15f, r.right)
        assertEquals(15f, r.bottom)
        assertEquals(10f, r.width)
    }

    @Test
    fun `sub pixel offsets accumulate without rounding`() {
        var r = Rect.fromLTWH(0f, 0f, 10f, 10f)
        repeat(10) { r = r.offset(0.1f, 0f) }
        assertTrue(r.left in 0.99f..1.01f, "expected ~1.0 after ten 0.1 steps, got ${r.left}")
    }
}

class Vector2Test {

    @Test
    fun `packing round trips both components`() {
        val v = Vector2(3.5f, -2.25f)
        assertEquals(3.5f, v.x)
        assertEquals(-2.25f, v.y)
    }

    /**
     * The packing shifts x into the high 32 bits and masks y into the low ones. A negative y
     * sign-extends unless the mask is right, so it is the case most likely to break.
     */
    @Test
    fun `packing survives negatives zero and mixed signs`() {
        val cases = listOf(
            0f to 0f,
            -1f to -1f,
            -0.5f to 2f,
            2f to -0.5f,
            1e-8f to -1e8f,
        )
        for ((x, y) in cases) {
            val v = Vector2(x, y)
            assertEquals(x, v.x, "x lost for ($x, $y)")
            assertEquals(y, v.y, "y lost for ($x, $y)")
        }
    }

    @Test
    fun `zero is both components zero`() {
        assertEquals(0f, Vector2.Zero.x)
        assertEquals(0f, Vector2.Zero.y)
    }

    @Test
    fun `copy replaces only the named component`() {
        val v = Vector2(1f, 2f).copy(y = 9f)
        assertEquals(1f, v.x)
        assertEquals(9f, v.y)
    }

    @Test
    fun `arithmetic operates componentwise`() {
        val sum = Vector2(1f, 2f) + Vector2(3f, 4f)
        assertEquals(4f, sum.x)
        assertEquals(6f, sum.y)

        val diff = Vector2(5f, 5f) - Vector2(1f, 2f)
        assertEquals(4f, diff.x)
        assertEquals(3f, diff.y)

        val scaled = Vector2(1.5f, -2f) * 2f
        assertEquals(3f, scaled.x)
        assertEquals(-4f, scaled.y)
    }
}
