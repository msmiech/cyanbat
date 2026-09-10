package at.smiech.engine.impl

import at.smiech.engine.Controls
import at.smiech.engine.Direction
import at.smiech.engine.GameButton
import kotlin.math.abs

/**
 * Turns raw key, pad and gesture state into the [Controls] the game reads - the keyboard and
 * controller counterpart of [PointerTouchHandler].
 *
 * Platform-neutral: hosts feed it through the small [onDirection] / [onAxis] / [onButton] surface,
 * via a Compose adapter on desktop and an `android.view` one on Android. Adding a device means
 * writing another adapter, not touching this class or any screen.
 *
 * Threading: NOT synchronised, for the same reason [PointerTouchHandler] is not. Key and motion
 * callbacks arrive on the host's UI thread, which is the thread the game loop runs on.
 */
class ControlHandler : Controls {
    private val heldDirections = BooleanArray(Direction.entries.size)
    private val heldButtons = BooleanArray(GameButton.entries.size)

    /** Presses seen but not yet read. Separate from [heldButtons] so a tap shorter than a frame
     *  still registers, and so a held key registers exactly once. */
    private val pendingPresses = BooleanArray(GameButton.entries.size)

    private var axisX = 0f
    private var axisY = 0f

    // A key beats the stick it shares an axis with. They are rarely both live, and when they are
    // the deliberate press is the better guess at intent than a stick resting off centre.
    override val moveX: Float
        get() = digital(Direction.LEFT, Direction.RIGHT) ?: axisX

    override val moveY: Float
        get() = digital(Direction.UP, Direction.DOWN) ?: axisY

    private fun digital(negative: Direction, positive: Direction): Float? {
        val value = (if (heldDirections[positive.ordinal]) 1f else 0f) -
            (if (heldDirections[negative.ordinal]) 1f else 0f)
        return if (value == 0f) null else value
    }

    override fun consumePress(button: GameButton): Boolean {
        val index = button.ordinal
        val pressed = pendingPresses[index]
        pendingPresses[index] = false
        return pressed
    }

    fun onDirection(direction: Direction, pressed: Boolean) {
        heldDirections[direction.ordinal] = pressed
    }

    /**
     * Absolute stick position, both axes at once.
     *
     * The dead zone is rescaled rather than merely cut out, so the first pixel of real travel
     * moves the bat slowly instead of jumping it to a fifth of full speed.
     */
    fun onAxis(x: Float, y: Float) {
        axisX = applyDeadZone(x)
        axisY = applyDeadZone(y)
    }

    private fun applyDeadZone(value: Float): Float {
        val magnitude = abs(value)
        if (magnitude < AXIS_DEAD_ZONE) return 0f
        val rescaled = (magnitude - AXIS_DEAD_ZONE) / (1f - AXIS_DEAD_ZONE)
        return (if (value < 0f) -rescaled else rescaled).coerceIn(-1f, 1f)
    }

    /**
     * A button that reports both edges. The press is recorded on the way down only, so a host
     * repeating key-downs while a key is held - as Android does - still yields one press.
     */
    fun onButton(button: GameButton, pressed: Boolean) {
        val index = button.ordinal
        if (pressed && !heldButtons[index]) pendingPresses[index] = true
        heldButtons[index] = pressed
    }

    /**
     * A press with no release to come: a system gesture, or a button the host only reports once.
     */
    fun onButtonPress(button: GameButton) {
        pendingPresses[button.ordinal] = true
    }

    /**
     * Forgets everything held.
     *
     * Hosts call this when they stop receiving input mid-press - a window losing focus, an
     * activity going to the background - because the matching release is delivered to whoever has
     * focus now, and without it the bat would fly off on a key nobody is pressing any more.
     */
    fun releaseAll() {
        heldDirections.fill(false)
        heldButtons.fill(false)
        pendingPresses.fill(false)
        axisX = 0f
        axisY = 0f
    }

    companion object {
        /** Stick travel ignored as rest position, as a fraction of full deflection. */
        const val AXIS_DEAD_ZONE = 0.2f
    }
}
