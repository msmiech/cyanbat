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

/**
 * The cutscene between a stage's boss going down and its second boss's fight (see
 * [at.smiech.cyanbat.service.StageDesign.encore]), as time: when what the first boss bought is
 * offered, when the black bars come in, when the second boss comes, and where the bat is meanwhile.
 * [GameScreen] plays it, and the second boss's brain flies its own part, ending it when its fight
 * begins.
 *
 * The first boss's wreck goes up as any boss's does, with the bat still the player's to fly, and any
 * level up it bought is offered. Then the bars come in, and the bat is taken out of the player's
 * hands and glides to a mark; once the bars are in, the second boss comes for it. Its bite throws
 * the bat back and down a little, where it hangs until the fight.
 *
 * Its time is the run's own, so a pause holds it, and so does a level up being offered.
 */
class Interlude {
    /** Seconds since the first boss went down. */
    var seconds = 0f
        private set

    /** Where the bat was when it was taken out of the player's hands, once it has been. */
    private var fromX = Float.NaN
    private var fromY = Float.NaN

    /** When the bite landed, once it has. */
    private var bittenAt = Float.NaN

    /**
     * Whether a level up the first boss bought is to be offered now: once its wreck has gone up and
     * before the bars come in.
     */
    val offersDue: Boolean get() = seconds >= INTERLUDE_OFFER_SECONDS && !holdsBat

    /** Whether the bat is out of the player's hands: from when the bars start to come in. */
    val holdsBat: Boolean get() = seconds >= INTERLUDE_HOLD_SECONDS

    /** Whether the second boss is due: once the bars are in. */
    val viperDue: Boolean get() = seconds >= INTERLUDE_HOLD_SECONDS + LETTERBOX_SECONDS

    /** Whether the bat has been taken to be held; see [take]. */
    val batTaken: Boolean get() = !fromX.isNaN()

    /** How far in the black bars are, 0..1. */
    val bars: Float
        get() = ((seconds - INTERLUDE_HOLD_SECONDS) / LETTERBOX_SECONDS).coerceIn(0f, 1f)

    fun advance(deltaTime: Float) {
        seconds += deltaTime
    }

    /** Takes the bat from the player where its middle is, at ([x], [y]). */
    fun take(x: Float, y: Float) {
        fromX = x
        fromY = y
    }

    /** The bite has landed. */
    fun bite() {
        if (bittenAt.isNaN()) bittenAt = seconds
    }

    /** Where the bat's middle is to be now, across: see [batY]. */
    val batX: Float get() = held(fromX, INTERLUDE_MARK_X, INTERLUDE_RECOIL_X)

    /**
     * Where the bat's middle is to be now, down: gliding from where it was taken to its mark, and
     * thrown back from there by the bite.
     */
    val batY: Float get() = held(fromY, INTERLUDE_MARK_Y, INTERLUDE_RECOIL_Y)

    private fun held(from: Float, mark: Float, recoil: Float): Float {
        val glide = eased((seconds - INTERLUDE_HOLD_SECONDS) / INTERLUDE_GLIDE_SECONDS)
        val at = from + (mark - from) * glide
        if (bittenAt.isNaN()) return at
        return at + recoil * eased((seconds - bittenAt) / INTERLUDE_RECOIL_SECONDS)
    }

    private companion object {
        /** Smoothstep over 0..1. */
        fun eased(t: Float): Float {
            val c = t.coerceIn(0f, 1f)
            return c * c * (3f - 2f * c)
        }
    }
}
