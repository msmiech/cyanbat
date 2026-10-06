package at.smiech.engine.impl

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import at.smiech.engine.Direction
import at.smiech.engine.GameButton

/**
 * Feeds Compose key events into [ControlHandler]: the desktop's counterpart to
 * [onComposePointerEvent]. Android drives the same handler from `android.view` events instead,
 * because a game controller's input never reaches a Compose focus target.
 *
 * @return true when the game claimed the key, which a Compose `onKeyEvent` returns to stop the
 *   event propagating.
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
 * The key mapping, split out from the event so it can be tested: a Compose [KeyEvent] cannot be
 * built by hand outside a window, since it wraps a platform type the toolkit fills in.
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

/** WASD and the arrow keys, so either hand can steer. */
private fun directionOf(key: Key): Direction? = when (key) {
    Key.A, Key.DirectionLeft -> Direction.LEFT
    Key.D, Key.DirectionRight -> Direction.RIGHT
    Key.W, Key.DirectionUp -> Direction.UP
    Key.S, Key.DirectionDown -> Direction.DOWN
    else -> null
}

/** The game's buttons on a keyboard. */
private fun buttonOf(key: Key): GameButton? = when (key) {
    Key.Escape, Key.P -> GameButton.PAUSE
    Key.Q, Key.Backspace -> GameButton.BACK
    Key.Enter, Key.Spacebar -> GameButton.CONFIRM
    // Both number rows: the number pad is closest to a hand resting on the arrow keys.
    Key.One, Key.NumPad1 -> GameButton.CHOICE_1
    Key.Two, Key.NumPad2 -> GameButton.CHOICE_2
    Key.Three, Key.NumPad3 -> GameButton.CHOICE_3
    else -> null
}
