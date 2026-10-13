package at.smiech.engine

import kotlin.math.min
import kotlin.math.sqrt

/**
 * An analog stick on a touch screen that is not there until a thumb is: wherever a thumb is put
 * down becomes its center, and moving the thumb from there is read as a deflection, the way a game
 * pad's stick is, for a player who would rather steer the bat than drag it. Letting go takes it
 * away again.
 *
 * Starting where the thumb lands, the stick always starts at rest under it, and the player never
 * has to look for it: nothing is drawn, and nothing is in the way, until the thumb is down.
 *
 * It holds one thumb and ignores every other finger, as the drag does. Positions are in frame
 * pixels. Not synchronized, for the same reason as the touch handler: it is fed and read on the
 * game loop's thread.
 */
class VirtualStick(
    /**
     * How far the thumb has to move from the center for full speed, and how far the knob goes: the
     * further, the less sensitive. A host may change it between updates, as the player's setting
     * arrives.
     */
    var radius: Float,
) {
    /**
     * Whether the stick steers at all. Off, it holds nothing and reads nothing, and the bat is
     * dragged as it always was.
     */
    var enabled: Boolean = false
        set(value) {
            if (!value) release()
            field = value
        }

    /** The pointer whose thumb is on the stick, or [NO_POINTER]. */
    var pointer: Int = NO_POINTER
        private set

    /** Whether a thumb is on the stick, and so whether there is a stick to see. */
    val held: Boolean get() = pointer != NO_POINTER

    /** Where the thumb was put down, the stick's center while it is [held]. */
    var centerX: Float = 0f
        private set
    var centerY: Float = 0f
        private set

    /** Where the knob is, under the thumb, and never further from the center than [radius]. */
    var knobX: Float = 0f
        private set
    var knobY: Float = 0f
        private set

    /** The deflection, -1 hard left to +1 hard right, as [Controls.moveX]. */
    var moveX: Float = 0f
        private set

    /** -1 up to +1 down, as [Controls.moveY]. */
    var moveY: Float = 0f
        private set

    /**
     * Folds this update's touch [events] into the stick: which thumb holds it, and where that thumb
     * now is.
     *
     * Where the thumb is comes from [Input.getTouchX] rather than from the events, which are a
     * consuming read: an overlay that was up reads them instead, and a thumb held through it would
     * come back to a knob where it last saw it. The same goes for a thumb lifted meanwhile, which
     * [Input.isTouchDown] lets go of.
     */
    fun update(input: Input, events: List<Input.TouchEvent>?) {
        if (!enabled) return
        if (held && !input.isTouchDown(pointer)) release()
        if (events != null) {
            for (index in events.indices) {
                val event = events[index]
                if (event.type == Input.TouchEvent.TOUCH_UP) {
                    if (event.pointer == pointer) release()
                    continue
                }
                // A drag claims it as well as a touch, so a finger already down when an overlay
                // closed, which read its touch, takes the stick as soon as it moves. Only a pointer
                // that is down, though: a mouse reports motion with no button held, and hovering
                // is not holding.
                if (!held && input.isTouchDown(event.pointer)) {
                    grab(event.pointer, event.x.toFloat(), event.y.toFloat())
                }
            }
        }
        if (held) follow(input.getTouchX(pointer).toFloat(), input.getTouchY(pointer).toFloat())
    }

    /** Lets go of the thumb, if one is on it, which takes the stick away. */
    fun release() {
        pointer = NO_POINTER
        knobX = centerX
        knobY = centerY
        moveX = 0f
        moveY = 0f
    }

    private fun grab(pointer: Int, x: Float, y: Float) {
        this.pointer = pointer
        centerX = x
        centerY = y
    }

    /**
     * Puts the knob under the thumb at ([x], [y]), or on the rim toward it, and reads the
     * deflection off it.
     *
     * Radial: the dead zone is a disc round the center and the direction is kept exactly, where a
     * pad's stick is cut axis by axis. Travel past the dead zone is rescaled, as the pad's is, so
     * the first bit of it moves the bat slowly rather than jumping it to a fifth of full speed, and
     * full speed comes a little short of the rim, which a thumb cannot feel for.
     */
    private fun follow(x: Float, y: Float) {
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx * dx + dy * dy)
        val reach = min(distance, radius)
        val scale = if (distance > 0f) reach / distance else 0f
        knobX = centerX + dx * scale
        knobY = centerY + dy * scale

        val travel = ((reach / radius - DEAD_ZONE) / (FULL_SPEED_AT - DEAD_ZONE)).coerceIn(0f, 1f)
        if (travel == 0f) {
            moveX = 0f
            moveY = 0f
        } else {
            moveX = dx / distance * travel
            moveY = dy / distance * travel
        }
    }

    companion object {
        const val NO_POINTER = -1

        /** How far the thumb can wander from the center, as a share of [radius], and not steer. */
        const val DEAD_ZONE = 0.12f

        /** How far out, as a share of [radius], the thumb has to be for full speed. */
        const val FULL_SPEED_AT = 0.9f
    }
}
