package at.smiech.engine.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import at.smiech.engine.Controls
import at.smiech.engine.Direction
import at.smiech.engine.GameButton
import at.smiech.engine.InputMethod
import kotlin.math.abs

/**
 * Turns raw key, pad and gesture state into the [Controls] the game reads: the keyboard and
 * controller counterpart of [PointerTouchHandler].
 *
 * Platform-neutral: hosts feed it through [onDirection], [onAxis] and [onButton], via a Compose
 * adapter on the desktop and an `android.view` one on Android. A new device needs only a new
 * adapter.
 *
 * Not synchronized, for the same reason as [PointerTouchHandler]: key and motion callbacks arrive
 * on the host's UI thread, which is the thread the game loop runs on.
 *
 * @param initialMethod what the player is taken to play with until they use something: whatever
 *   the platform is usually played with, or whatever started the run.
 */
class ControlHandler(initialMethod: InputMethod = InputMethod.KEYBOARD_MOUSE) : Controls {
    /**
     * What the player last played with, as the adapters report it through [use]. Snapshot state, so
     * a menu shown by Compose follows it as the game's screens do.
     */
    override var inputMethod: InputMethod by mutableStateOf(initialMethod)
        private set

    /**
     * The player has just used [method]: pressed one of its keys or buttons, pushed its stick, or
     * moved, clicked or touched the pointer.
     */
    fun use(method: InputMethod) {
        if (inputMethod != method) inputMethod = method
    }

    private val heldDirections = BooleanArray(Direction.entries.size)
    private val heldButtons = BooleanArray(GameButton.entries.size)

    /**
     * Presses seen but not yet read. Separate from [heldButtons] so a tap shorter than a frame
     * still registers, and a held key registers exactly once.
     */
    private val pendingPresses = BooleanArray(GameButton.entries.size)

    private var axisX = 0f
    private var axisY = 0f

    // A key beats the stick on the same axis. They are rarely both live, and when they are, the
    // deliberate press says more about intent than a stick resting off center.
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

    /** A digital direction pressed or released. */
    fun onDirection(direction: Direction, pressed: Boolean) {
        heldDirections[direction.ordinal] = pressed
    }

    /**
     * Absolute stick position, both axes at once.
     *
     * Travel past the dead zone is rescaled rather than just cut, so the first bit of real travel
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
     * A button that reports both edges. The press is recorded on the way down only, so a host that
     * repeats key-downs while a key is held, as Android does, still yields one press.
     */
    fun onButton(button: GameButton, pressed: Boolean) {
        val index = button.ordinal
        if (pressed && !heldButtons[index]) pendingPresses[index] = true
        heldButtons[index] = pressed
    }

    /** A press with no release to follow: a system gesture, or a button the host reports once. */
    fun onButtonPress(button: GameButton) {
        pendingPresses[button.ordinal] = true
    }

    /**
     * Forgets everything held.
     *
     * Hosts call this when they stop receiving input mid-press, as when a window loses focus or an
     * activity goes to the background: the release goes to whoever has focus now, and without this
     * the bat would keep flying on a key nobody is pressing.
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

        /**
         * How far a stick has to be pushed to count as the player picking the pad up: past the dead
         * zone and then some, as a pad left on the table can report a stick a little off center.
         */
        const val PAD_USE_DEFLECTION = 0.5f
    }
}
