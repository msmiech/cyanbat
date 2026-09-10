package at.smiech.engine.impl

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import at.smiech.engine.Direction
import at.smiech.engine.GameButton

/**
 * Feeds Compose key events into [ControlHandler]. Desktop's counterpart to
 * [onComposePointerEvent]; Android drives the same handler from `android.view` events instead,
 * because a game controller's input never reaches a Compose focus target.
 *
 * @return true when the game claimed the key, which is what a Compose `onKeyEvent` hands back to
 *   stop the event travelling any further.
 */
fun ControlHandler.onComposeKeyEvent(event: KeyEvent): Boolean {
    val pressed = when (event.type) {
        KeyEventType.KeyDown -> true
        KeyEventType.KeyUp -> false
        else -> return false
    }
    return applyKey(event.key, pressed)
}

/**
 * The mapping itself, split out from the event so it can be tested.
 *
 * A Compose [KeyEvent] cannot be built by hand off-window - it is a value class over an internal
 * type the toolkit fills in - so everything above this line is a shim that only unpacks the event,
 * and everything below it is covered.
 */
internal fun ControlHandler.applyKey(key: Key, pressed: Boolean): Boolean {
    directionOf(key)?.let {
        onDirection(it, pressed)
        return true
    }
    buttonOf(key)?.let {
        onButton(it, pressed)
        return true
    }
    return false
}

/** WASD and the arrow keys, so neither hand is the wrong one. */
private fun directionOf(key: Key): Direction? = when (key) {
    Key.A, Key.DirectionLeft -> Direction.LEFT
    Key.D, Key.DirectionRight -> Direction.RIGHT
    Key.W, Key.DirectionUp -> Direction.UP
    Key.S, Key.DirectionDown -> Direction.DOWN
    else -> null
}

private fun buttonOf(key: Key): GameButton? = when (key) {
    Key.Escape, Key.P -> GameButton.PAUSE
    Key.Q, Key.Backspace -> GameButton.BACK
    Key.Enter, Key.Spacebar -> GameButton.CONFIRM
    else -> null
}
