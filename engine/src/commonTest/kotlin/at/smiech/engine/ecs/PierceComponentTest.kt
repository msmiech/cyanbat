package at.smiech.engine.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bookkeeping behind a shot that survives what it hits.
 *
 * Both halves exist to stop the same failure: a shot and an enemy overlap for several frames, and
 * the collision system reports that pair on every one of them. Without the record, a single pass
 * would spend every pierce the shot owns and land its damage several times over.
 */
class PierceComponentTest {

    @Test
    fun `the first meeting with a target is not a repeat`() {
        val pierce = PierceComponent(remaining = 2)

        assertFalse(pierce.meet(targetId = 7))
    }

    @Test
    fun `every meeting after the first one is`() {
        val pierce = PierceComponent(remaining = 2)
        pierce.meet(targetId = 7)

        assertTrue(pierce.meet(targetId = 7))
        assertTrue(pierce.meet(targetId = 7))
    }

    /** The point of a piercing shot: the next enemy along is a new target, not the same one. */
    @Test
    fun `a different target is a fresh meeting`() {
        val pierce = PierceComponent(remaining = 2)
        pierce.meet(targetId = 7)

        assertFalse(pierce.meet(targetId = 8))
    }

    @Test
    fun `spending draws down the count`() {
        val pierce = PierceComponent(remaining = 2)

        assertTrue(pierce.spend())
        assertEquals(1, pierce.remaining)
        assertTrue(pierce.spend())
        assertEquals(0, pierce.remaining)
    }

    /** A shot out of pierce is a shot that is stopped, which is what ends its flight. */
    @Test
    fun `a spent shot stops going through`() {
        val pierce = PierceComponent(remaining = 1)
        pierce.spend()

        assertFalse(pierce.spend())
        assertEquals(0, pierce.remaining, "the count must not go negative")
    }

    @Test
    fun `a shot with no pierce to begin with is stopped by the first thing it meets`() {
        assertFalse(PierceComponent(remaining = 0).spend())
    }

    /**
     * The two together, over the frames a real overlap lasts: one enemy costs one pierce however
     * long the sprites sit on top of each other, and the shot still has the rest for what comes
     * next.
     */
    @Test
    fun `a long overlap with one enemy costs exactly one pierce`() {
        val pierce = PierceComponent(remaining = 3)

        repeat(8) { frame ->
            if (!pierce.meet(targetId = 7)) pierce.spend()
            assertEquals(2, pierce.remaining, "frame $frame spent a second pierce on the same enemy")
        }

        // And the next enemy along still takes one.
        if (!pierce.meet(targetId = 8)) pierce.spend()
        assertEquals(1, pierce.remaining)
    }
}
