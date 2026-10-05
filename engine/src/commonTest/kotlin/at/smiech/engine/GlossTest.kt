package at.smiech.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a sprite catches a light shining on it, worked out from nothing but its outline: on the side
 * the light comes from, along the edge, and nowhere else.
 */
class GlossTest {

    /** A disc of [radius] in a frame with a pixel of air round it. */
    private fun disc(radius: Int): Gloss.Surface {
        val size = 2 * radius + 3
        val middle = size / 2
        val pixels = IntArray(size * size) {
            val dx = it % size - middle
            val dy = it / size - middle
            if (dx * dx + dy * dy <= radius * radius) OPAQUE else 0
        }
        return Gloss.surface(pixels, size, size)
    }

    /** Where the glints are, relative to the middle of the frame. */
    private fun glinting(surface: Gloss.Surface, direction: Int): List<Pair<Int, Int>> {
        val levels = surface.glints(direction)
        val middle = surface.width / 2
        return levels.indices.filter { levels[it] > 0 }
            .map { it % surface.width - middle to it / surface.width - middle }
    }

    @Test
    fun `a disc glints on the side the light comes from whichever side that is`() {
        val surface = disc(9)
        for (direction in 0 until Gloss.DIRECTIONS) {
            val angle = direction * 2 * PI / Gloss.DIRECTIONS
            val glints = glinting(surface, direction)
            assertTrue(glints.isNotEmpty(), "nothing glints in a light from direction $direction")
            for ((x, y) in glints) {
                assertTrue(
                    x * cos(angle) + y * sin(angle) > 0,
                    "($x, $y) glints in a light from direction $direction"
                )
            }
        }
    }

    /** A rim of light along the edge, not a sheen across the middle. */
    @Test
    fun `a glint keeps to the edge`() {
        val radius = 9
        for (direction in 0 until Gloss.DIRECTIONS) {
            for ((x, y) in glinting(disc(radius), direction)) {
                assertTrue(
                    x * x + y * y >= (radius - 4) * (radius - 4),
                    "($x, $y) glints in the middle"
                )
            }
        }
    }

    /** Each level of glint is a step of its own, and air never glints. */
    @Test
    fun `glints come in steps and only on the picture`() {
        val surface = disc(9)
        for (direction in 0 until Gloss.DIRECTIONS) {
            val levels = surface.glints(direction)
            assertTrue(levels.all { it in 0..Gloss.LEVELS })
            assertTrue(
                levels.any { it.toInt() == Gloss.LEVELS },
                "nothing glints fully in a light from $direction"
            )
        }
        val corner = 0
        assertEquals(0, surface.glints(10)[corner].toInt())
    }

    /**
     * A rock standing on the floor is cut off at the bottom of its frame. That cut is not an edge, so a
     * light low beside it does not glint along it.
     */
    @Test
    fun `where the art runs off its frame it does not glint`() {
        val width = 20
        val height = 10
        // A mound filling the bottom of its frame, cut flat by the frame's bottom edge.
        val pixels = IntArray(width * height) { if (it / width >= 4) OPAQUE else 0 }
        val below = Gloss.DIRECTIONS / 4
        val glints = Gloss.surface(pixels, width, height).glints(below)

        val bottomRow = (0 until width).map { glints[(height - 1) * width + it].toInt() }
        assertTrue(bottomRow.all { it == 0 }, "the cut glints: $bottomRow")
    }

    private companion object {
        const val OPAQUE = 0xFF707070.toInt()
    }
}
