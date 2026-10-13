package at.smiech.engine

/**
 * A button drawn on a touch screen, over the run, such as the pause button of a platform that has
 * no Back for the player to pause with: a finger put down on it presses it, and is the button's
 * until it lifts, so it steers nothing meanwhile.
 *
 * It presses as the finger goes down rather than as it lifts, since what it does should happen at
 * once, as a key's would. Only a finger put down on it counts: one steering the bat that slides
 * over it does not press it.
 *
 * The area that takes a touch is in frame pixels, and is meant to be bigger than what is drawn: a
 * fingertip covers far more of the frame than a small icon does. Not synchronized, for the same
 * reason as [VirtualStick]: it is fed and read on the game loop's thread.
 */
class TouchButton(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** Whether the button is on the screen at all. Off, it holds nothing and reads nothing. */
    var enabled: Boolean = false
        set(value) {
            if (!value) {
                pointer = NO_POINTER
                pressed = false
            }
            field = value
        }

    /** The pointer whose finger went down on the button and has not lifted, or [NO_POINTER]. */
    var pointer: Int = NO_POINTER
        private set

    /** Whether a finger is on the button. */
    val held: Boolean get() = pointer != NO_POINTER

    private var pressed = false

    /**
     * Folds this update's touch [events] into the button. A finger lifted while something else read
     * the events, as an overlay does, is let go of through [Input.isTouchDown].
     */
    fun update(input: Input, events: List<Input.TouchEvent>?) {
        if (!enabled) return
        if (held && !input.isTouchDown(pointer)) pointer = NO_POINTER
        if (events == null) return
        for (index in events.indices) {
            val event = events[index]
            when (event.type) {
                Input.TouchEvent.TOUCH_UP -> if (event.pointer == pointer) pointer = NO_POINTER
                Input.TouchEvent.TOUCH_DOWN -> if (!held && contains(event.x, event.y)) {
                    pointer = event.pointer
                    pressed = true
                }
            }
        }
    }

    /** Whether the button was pressed since the last call; reading it clears it. */
    fun consumePress(): Boolean {
        val was = pressed
        pressed = false
        return was
    }

    /** Whether the frame pixel ([x], [y]) is on the button. */
    fun contains(x: Int, y: Int): Boolean = x >= left && x < right && y >= top && y < bottom

    companion object {
        const val NO_POINTER = -1
    }
}
