package at.smiech.engine.impl

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import at.smiech.engine.Direction
import at.smiech.engine.GameButton

/**
 * Feeds `android.view` key events into [ControlHandler]: hardware keyboards and game controllers
 * alike, since Android reports a pad's face buttons and d-pad as key codes.
 *
 * Driven from the activity rather than from a Compose `onKeyEvent`, because a controller's input
 * is not routed to a focused composable - it arrives at the window.
 *
 * @return true when the game claimed the key, so the activity knows not to pass it on.
 */
fun ControlHandler.onAndroidKeyEvent(event: KeyEvent): Boolean {
    val pressed = when (event.action) {
        KeyEvent.ACTION_DOWN -> true
        KeyEvent.ACTION_UP -> false
        else -> return false
    }

    directionOf(event.keyCode)?.let {
        onDirection(it, pressed)
        return true
    }
    buttonOf(event.keyCode)?.let {
        onButton(it, pressed)
        return true
    }
    return false
}

/**
 * Feeds a controller's analog sticks into [ControlHandler].
 *
 * Only joystick sources are read: the same callback also carries mouse wheels and trackpads,
 * which have nothing to do with steering the bat.
 *
 * @return true when the event was a stick reading the game took.
 */
fun ControlHandler.onAndroidMotionEvent(event: MotionEvent): Boolean {
    if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
    if (event.action != MotionEvent.ACTION_MOVE) return false

    // A pad's d-pad reports on the hat axes rather than as key codes, and it wins where it is
    // off centre: it is the deliberate, fully-deflected input of the two.
    val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
    val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
    onAxis(
        x = if (hatX != 0f) hatX else event.getAxisValue(MotionEvent.AXIS_X),
        y = if (hatY != 0f) hatY else event.getAxisValue(MotionEvent.AXIS_Y),
    )
    return true
}

/**
 * WASD and the arrow keys from a keyboard, the d-pad from a controller.
 *
 * A pad that reports its d-pad on the hat axes instead goes through [onAndroidMotionEvent]; pads
 * differ on which they use, so both paths exist.
 */
private fun directionOf(keyCode: Int): Direction? = when (keyCode) {
    KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> Direction.LEFT
    KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.RIGHT
    KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP -> Direction.UP
    KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN -> Direction.DOWN
    else -> null
}

/**
 * Note what is absent: `KEYCODE_BACK`. Gesture navigation raises no key at all for back, so the
 * activity takes it from the back-pressed dispatcher instead and this mapping would only ever
 * shadow it on older three-button setups.
 */
private fun buttonOf(keyCode: Int): GameButton? = when (keyCode) {
    KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_BUTTON_START -> GameButton.PAUSE
    KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_SELECT ->
        GameButton.BACK
    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_BUTTON_A -> GameButton.CONFIRM
    else -> null
}
