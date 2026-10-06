package at.smiech.cyanbat.ui.game

import at.smiech.engine.Input.TouchEvent

/**
 * Picks the taps out of a touch stream: releases of pointers that also went down while this
 * detector was listening.
 *
 * An overlay that opens mid-run opens under a finger still steering the bat, and that finger's
 * release is not an answer to the overlay; read as one, the level-up dialog picked whatever card
 * was under the fingertip. An arming delay only narrows that window; requiring the press closes it.
 *
 * A touch the system canceled is no tap either. Android's back gesture starts as a touch on the
 * game and is canceled once the system claims it; read as a tap, it resumed the pause screen just
 * before the Back arrived, which paused it again, so the player could never quit.
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
