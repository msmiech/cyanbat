package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.util.INTERLUDE_GLIDE_SECONDS
import at.smiech.cyanbat.util.INTERLUDE_HOLD_SECONDS
import at.smiech.cyanbat.util.INTERLUDE_MARK_X
import at.smiech.cyanbat.util.INTERLUDE_MARK_Y
import at.smiech.cyanbat.util.INTERLUDE_OFFER_SECONDS
import at.smiech.cyanbat.util.INTERLUDE_RECOIL_SECONDS
import at.smiech.cyanbat.util.INTERLUDE_RECOIL_X
import at.smiech.cyanbat.util.INTERLUDE_RECOIL_Y
import at.smiech.cyanbat.util.LETTERBOX_SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The cutscene between the lagoon's two bosses, as time: what comes when, and where the bat is. */
class InterludeTest {

    /** On to just past [seconds], so a sum of floats does not land a hair short of a moment. */
    private fun Interlude.at(seconds: Float): Interlude =
        apply { advance(seconds - this.seconds + 1e-4f) }

    /** The first boss's wreck goes up with the bat still the player's, and nothing else happens yet. */
    @Test
    fun `the wreck goes up before anything else`() {
        val interlude = Interlude().at(INTERLUDE_OFFER_SECONDS - 0.1f)
        assertFalse(interlude.offersDue)
        assertFalse(interlude.holdsBat)
        assertFalse(interlude.viperDue)
        assertEquals(0f, interlude.bars)
    }

    /** Picked by a player who thinks the stage is won: after the wreck, before the bars. */
    @Test
    fun `a level up is offered between the wreck and the bars`() {
        val interlude = Interlude().at(INTERLUDE_OFFER_SECONDS)
        assertTrue(interlude.offersDue)
        interlude.at(INTERLUDE_HOLD_SECONDS)
        assertFalse(interlude.offersDue, "still offering once the bars came in")
    }

    /** The bars hide the second boss's health bar, so it comes once they are in. */
    @Test
    fun `the bars come in and then the second boss comes`() {
        val interlude = Interlude().at(INTERLUDE_HOLD_SECONDS)
        assertTrue(interlude.holdsBat)
        assertFalse(interlude.viperDue)
        interlude.at(INTERLUDE_HOLD_SECONDS + LETTERBOX_SECONDS / 2f)
        assertEquals(0.5f, interlude.bars, 0.01f)
        assertFalse(interlude.viperDue)
        interlude.at(INTERLUDE_HOLD_SECONDS + LETTERBOX_SECONDS)
        assertEquals(1f, interlude.bars)
        assertTrue(interlude.viperDue)
    }

    @Test
    fun `the bat glides to its mark and the bite throws it back`() {
        val interlude = Interlude().at(INTERLUDE_HOLD_SECONDS)
        assertFalse(interlude.batTaken)
        interlude.take(500f, 60f)
        assertTrue(interlude.batTaken)
        assertEquals(500f, interlude.batX, 0.01f)
        assertEquals(60f, interlude.batY, 0.01f)

        interlude.at(INTERLUDE_HOLD_SECONDS + INTERLUDE_GLIDE_SECONDS)
        assertEquals(INTERLUDE_MARK_X, interlude.batX, 0.01f)
        assertEquals(INTERLUDE_MARK_Y, interlude.batY, 0.01f)

        interlude.bite()
        interlude.at(interlude.seconds + INTERLUDE_RECOIL_SECONDS)
        assertEquals(INTERLUDE_MARK_X + INTERLUDE_RECOIL_X, interlude.batX, 0.01f)
        assertEquals(INTERLUDE_MARK_Y + INTERLUDE_RECOIL_Y, interlude.batY, 0.01f)
    }
}
