package at.smiech.engine.impl

import at.smiech.engine.GameButton
import at.smiech.engine.InputMethod
import kotlin.math.abs

/**
 * A game controller's state at one poll, for a host that polls pads rather than being sent their
 * events, as the desktop does through SDL. In the layout SDL and the browsers lay every pad out in:
 * [south] is the bottom face button (an Xbox pad's A, a PlayStation pad's Cross), [east] the right
 * one (B, Circle).
 */
class PadState {
    /** Which kind of pad this is, for its prompts. */
    var method = InputMethod.XBOX_PAD

    /** The left stick, -1 to 1 each way; y grows downward, as the frame's does. */
    var stickX = 0f
    var stickY = 0f

    var dpadLeft = false
    var dpadRight = false
    var dpadUp = false
    var dpadDown = false

    var south = false
    var east = false

    /** Start, Menu or Options: the button right of center. */
    var start = false

    /** Back, View or Create: the button left of center. */
    var select = false

    /** The d-pad as an axis value, -1, 0 or 1. */
    val dpadX: Float get() = (if (dpadRight) 1f else 0f) - (if (dpadLeft) 1f else 0f)
    val dpadY: Float get() = (if (dpadDown) 1f else 0f) - (if (dpadUp) 1f else 0f)

    fun clear() {
        stickX = 0f
        stickY = 0f
        dpadLeft = false
        dpadRight = false
        dpadUp = false
        dpadDown = false
        south = false
        east = false
        start = false
        select = false
    }

    fun copyFrom(other: PadState) {
        method = other.method
        stickX = other.stickX
        stickY = other.stickY
        dpadLeft = other.dpadLeft
        dpadRight = other.dpadRight
        dpadUp = other.dpadUp
        dpadDown = other.dpadDown
        south = other.south
        east = other.east
        start = other.start
        select = other.select
    }
}

/** A key a pad presses in a menu, which the host turns into the key the menu reads it as. */
enum class PadMenuKey { UP, DOWN, LEFT, RIGHT, CONFIRM, BACK }

/**
 * Turns polled [PadState]s into the game's controls during a run, and into a menu's keys outside
 * one: the polling hosts' counterpart of [onAndroidKeyEvent], since a pad polled is no more than
 * its state, and the menu, which Compose drives, reads nothing but keys.
 *
 * In a run, the d-pad and the stick fly the bat, the d-pad winning while it is pressed; the bottom
 * face button confirms, the right one goes back, and Start pauses. In a menu, the d-pad and the
 * stick move the cursor, repeating while held as a key does, the bottom face button takes what it
 * is on, and the right one goes back.
 *
 * @param onMenuKey presses ([pressed] true) and lets go of a menu's key. A key held down is pressed
 *   again on its repeats, with no let go between, as a keyboard's are.
 */
class PadRouter(
    private val controls: ControlHandler,
    private val onMenuKey: (key: PadMenuKey, pressed: Boolean) -> Unit,
) {
    private val previous = PadState()
    private val idle = PadState()

    /** Whether the last update was in a run; null before the first. */
    private var lastInGame: Boolean? = null

    /** The menu direction held down, and when it next repeats. */
    private var heldDirection: PadMenuKey? = null
    private var repeatAt = 0L

    /**
     * Reads [pad], or nothing held when it is null: no pad connected, or the window not at the
     * player.
     *
     * @param inGame whether a run is up, which takes the pad as controls, or the menu, which takes
     *   it as keys.
     * @param nowMillis a clock for the menu's repeats.
     */
    fun update(pad: PadState?, inGame: Boolean, nowMillis: Long) {
        val state = pad ?: idle.also { it.clear() }
        if (inGame != lastInGame) {
            lastInGame?.let { letGo(wasInGame = it) }
            lastInGame = inGame
            // Whatever is held as the game and the menu hand over stays with the side it was
            // pressed on: the A that started a run is not a confirm in it, the B that quit one not
            // a back in the menu.
            previous.copyFrom(state)
            if (inGame) controls.onAxis(0f, 0f)
            return
        }
        if (pad != null && wasUsed(pad)) controls.use(pad.method)
        if (inGame) toGame(state) else toMenu(state, nowMillis)
        previous.copyFrom(state)
    }

    /** Whether the player did something with [pad] since the last poll. */
    private fun wasUsed(pad: PadState): Boolean =
        (pad.south && !previous.south) || (pad.east && !previous.east) ||
                (pad.start && !previous.start) || (pad.select && !previous.select) ||
                (pad.dpadX != 0f && pad.dpadX != previous.dpadX) ||
                (pad.dpadY != 0f && pad.dpadY != previous.dpadY) ||
                abs(pad.stickX) > ControlHandler.PAD_USE_DEFLECTION ||
                abs(pad.stickY) > ControlHandler.PAD_USE_DEFLECTION

    private fun toGame(pad: PadState) {
        if (pad.south != previous.south) controls.onButton(GameButton.CONFIRM, pad.south)
        // The pad's back and its View or Create both step back, as on Android, where the system
        // reads Select as Back.
        val back = pad.east || pad.select
        if (back != (previous.east || previous.select)) controls.onButton(GameButton.BACK, back)
        if (pad.start != previous.start) controls.onButton(GameButton.PAUSE, pad.start)
        // A d-pad off center wins over the stick, being the deliberate, fully deflected input of
        // the two, as Android's hat axes do.
        controls.onAxis(
            x = if (pad.dpadX != 0f) pad.dpadX else pad.stickX.coerceIn(-1f, 1f),
            y = if (pad.dpadY != 0f) pad.dpadY else pad.stickY.coerceIn(-1f, 1f),
        )
    }

    private fun toMenu(pad: PadState, nowMillis: Long) {
        val direction = menuDirection(pad)
        if (direction != heldDirection) {
            heldDirection?.let { onMenuKey(it, false) }
            direction?.let { onMenuKey(it, true) }
            heldDirection = direction
            repeatAt = nowMillis + MENU_REPEAT_DELAY_MILLIS
        } else if (direction != null && nowMillis >= repeatAt) {
            onMenuKey(direction, true)
            repeatAt = nowMillis + MENU_REPEAT_INTERVAL_MILLIS
        }
        if (pad.south != previous.south) onMenuKey(PadMenuKey.CONFIRM, pad.south)
        if (pad.east != previous.east) onMenuKey(PadMenuKey.BACK, pad.east)
    }

    /**
     * The way [pad] points the menu's cursor: the d-pad's, or the stick's, pushed well past its dead
     * zone and along whichever axis it leans more.
     */
    private fun menuDirection(pad: PadState): PadMenuKey? {
        val x = if (pad.dpadX != 0f) pad.dpadX else pad.stickX
        val y = if (pad.dpadY != 0f) pad.dpadY else pad.stickY
        if (maxOf(abs(x), abs(y)) < MENU_STICK_DEFLECTION) return null
        return if (abs(x) > abs(y)) {
            if (x < 0f) PadMenuKey.LEFT else PadMenuKey.RIGHT
        } else {
            if (y < 0f) PadMenuKey.UP else PadMenuKey.DOWN
        }
    }

    /** Lets go of whatever the pad holds on the side it is leaving. */
    private fun letGo(wasInGame: Boolean) {
        if (wasInGame) {
            if (previous.south) controls.onButton(GameButton.CONFIRM, false)
            if (previous.east || previous.select) controls.onButton(GameButton.BACK, false)
            if (previous.start) controls.onButton(GameButton.PAUSE, false)
            controls.onAxis(0f, 0f)
        } else {
            heldDirection?.let { onMenuKey(it, false) }
            heldDirection = null
            if (previous.south) onMenuKey(PadMenuKey.CONFIRM, false)
            if (previous.east) onMenuKey(PadMenuKey.BACK, false)
        }
    }

    companion object {
        /** How far the stick has to lean to move a menu's cursor. */
        const val MENU_STICK_DEFLECTION = 0.5f

        /** How long a direction is held before it repeats, and then how often, as keys do. */
        const val MENU_REPEAT_DELAY_MILLIS = 400L
        const val MENU_REPEAT_INTERVAL_MILLIS = 120L
    }
}
