package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.util.POISON_ONSET
import at.smiech.cyanbat.util.POISON_REST
import at.smiech.cyanbat.util.TICK_INITIAL
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Sky Viper's venom in the bat: how strongly it shows, and how far it ripples the frame. */
class PoisonTest {

    private val rows = 360

    private fun Poison.run(seconds: Float) =
        repeat((seconds / TICK_INITIAL).toInt()) { update(TICK_INITIAL) }

    private fun Poison.reach(): Int = ripple(rows).maxOf { abs(it) }

    @Test
    fun `nothing shows until the bite`() {
        val poison = Poison()
        poison.surge()
        poison.run(1f)
        assertFalse(poison.showing)
        assertFalse(poison.poisoned)
        assertEquals(0, poison.reach())
    }

    /** The venom going in is the worst of it, and it settles to the slight ripple the fight is flown through. */
    @Test
    fun `the bite takes hold at its strongest and settles to a ripple of a pixel`() {
        val poison = Poison()
        poison.take()
        assertEquals(POISON_ONSET, poison.strength)
        assertTrue(poison.reach() >= 2, "the bite barely rippled the frame: ${poison.reach()}")

        poison.run(10f)
        assertEquals(POISON_REST, poison.strength)
        assertEquals(1, poison.reach(), "at its rest it ripples rows by ${poison.reach()}")
        assertTrue(poison.poisoned)
    }

    /** Every offset is a whole frame pixel, and the ripple moves, so the frame swims rather than sits bent. */
    @Test
    fun `the ripple rolls on`() {
        val poison = Poison()
        poison.take()
        val before = poison.ripple(rows).copyOf()
        poison.run(0.3f)
        assertTrue(!before.contentEquals(poison.ripple(rows)), "the ripple stood still")
    }

    @Test
    fun `a blow stirs it up again`() {
        val poison = Poison()
        poison.take()
        poison.run(10f)
        poison.surge()
        assertTrue(poison.strength > POISON_REST, "a blow left it at ${poison.strength}")
        poison.run(10f)
        assertEquals(POISON_REST, poison.strength)
    }

    @Test
    fun `it lifts once cured`() {
        val poison = Poison()
        poison.take()
        poison.run(2f)
        poison.cure()
        assertFalse(poison.poisoned)
        assertTrue(poison.showing, "it vanished at once rather than lifting")
        poison.run(3f)
        assertFalse(poison.showing)
        assertEquals(0, poison.reach())
    }
}
