package at.smiech.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pattern a see-through sprite is drawn in; see [Dither]. */
class DitherTest {

    /** The pixels of one tile that [level] keeps. */
    private fun kept(level: Int): Set<Pair<Int, Int>> =
        (0 until Dither.TILE).flatMap { y -> (0 until Dither.TILE).map { x -> x to y } }
            .filter { (x, y) -> Dither.keeps(x, y, level) }
            .toSet()

    @Test
    fun `each level keeps that many pixels of every tile`() {
        for (level in 0..Dither.LEVELS) assertEquals(level, kept(level).size, "level $level")
    }

    /** Stepping up a level only ever adds pixels, so a pulse dissolves rather than flickers. */
    @Test
    fun `each level keeps every pixel the level below it keeps`() {
        for (level in 1..Dither.LEVELS) {
            assertTrue(kept(level).containsAll(kept(level - 1)), "level $level dropped a pixel")
        }
    }

    /** Spread evenly rather than bunched: at half, no two kept pixels are side by side. */
    @Test
    fun `half keeps a checkerboard`() {
        val checkerboard = (0 until Dither.TILE).flatMap { y ->
            (0 until Dither.TILE).map { x -> x to y }
        }.filter { (x, y) -> (x + y) % 2 == 0 }.toSet()

        assertEquals(checkerboard, kept(Dither.LEVELS / 2))
    }

    @Test
    fun `the pattern repeats across a picture`() {
        for (level in 0..Dither.LEVELS) for (y in 0 until 3 * Dither.TILE) for (x in 0 until 3 * Dither.TILE) {
            assertEquals(
                Dither.keeps(x % Dither.TILE, y % Dither.TILE, level),
                Dither.keeps(x, y, level),
                "level $level at $x, $y",
            )
        }
    }

    @Test
    fun `a coverage comes out as the nearest level and never past either end`() {
        assertEquals(0, Dither.level(-0.5f))
        assertEquals(0, Dither.level(0f))
        assertEquals(4, Dither.level(0.26f))
        assertEquals(Dither.LEVELS / 2, Dither.level(0.5f))
        assertEquals(Dither.LEVELS, Dither.level(1f))
        assertEquals(Dither.LEVELS, Dither.level(3f))
    }
}
