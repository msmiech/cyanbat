package at.smiech.cyanbat.ui.game

import at.smiech.engine.Input.TouchEvent

/**
 * Picks the taps out of a touch stream: releases of pointers that also went *down* while this
 * detector was listening.
 *
 * An overlay that opens mid-run opens under a finger that is still steering the bat. When that
 * finger lifts, its release is not the player answering the overlay - they never pressed anything
 * on it - but a bare "any TOUCH_UP" check reads it as exactly that: the level up dialog picked
 * whichever card happened to be under the fingertip. An arming delay only narrows that window,
 * because a finger can stay down for as long as it likes. Requiring the press as well closes it.
 *
 * A touch the system canceled is no tap either, though it went down and came up while listening:
 * Android's back gesture starts as a touch on the game and is canceled once the system takes it
 * over. Read as a tap, it resumed the pause screen just before the Back it was meant to quit it
 * with arrived - and that Back paused the game again, so the player could never get out.
 *
 * Call [reset] when the overlay opens, then hand every frame's events to [taps].
 */
internal class TapDetector {
    private val pressed = mutableSetOf<Int>()

    /** Forgets every pointer seen so far, so only presses from here on can become taps. */
    fun reset() {
        pressed.clear()
    }

    /**
     * The releases in [events] that complete a tap, in order.
     *
     * The events are the input's pooled objects, so read them before the next frame's
     * `touchEvents` call recycles them.
     */
    fun taps(events: List<TouchEvent>): List<TouchEvent> {
        if (events.isEmpty()) return emptyList()
        val result = ArrayList<TouchEvent>()
        for (event in events) {
            when (event.type) {
                TouchEvent.TOUCH_DOWN -> pressed += event.pointer
                TouchEvent.TOUCH_UP -> if (pressed.remove(event.pointer) && !event.canceled) result += event
            }
        }
        return result
    }
}
