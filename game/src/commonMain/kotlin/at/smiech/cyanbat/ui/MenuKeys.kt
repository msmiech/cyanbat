package at.smiech.cyanbat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp

/**
 * What a key asks of the menu, named for its meaning rather than for the key, as the game's own
 * `GameButton`s are: a keyboard and a game pad say the same things with different keys.
 */
internal enum class MenuKey(val direction: FocusDirection?) {
    UP(FocusDirection.Up),
    DOWN(FocusDirection.Down),
    LEFT(FocusDirection.Left),
    RIGHT(FocusDirection.Right),

    /** Take what the cursor is on. The control under it reads the key itself. */
    CONFIRM(null),

    /** Out of the screen, to the one before it. */
    BACK(null),
}

/**
 * The menu's keys: the same arrows and WASD the bat is flown with, and Enter, Space or Escape as any
 * menu takes them. A pad's d-pad and stick reach the menu as arrows, which Android makes of them
 * itself, and its A and B buttons as themselves.
 */
internal fun menuKeyOf(key: Key): MenuKey? = when (key) {
    Key.DirectionUp, Key.W -> MenuKey.UP
    Key.DirectionDown, Key.S -> MenuKey.DOWN
    Key.DirectionLeft, Key.A -> MenuKey.LEFT
    Key.DirectionRight, Key.D -> MenuKey.RIGHT
    Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter, Key.ButtonA -> MenuKey.CONFIRM
    Key.Escape, Key.Backspace, Key.ButtonB -> MenuKey.BACK
    else -> null
}

/** Where a screen's cursor starts; set by [HomeCursor], read by [MenuKeys]. */
private class MenuHome {
    var requester: FocusRequester? = null
}

/** The [MenuHome] of the [MenuKeys] around a screen. */
private val LocalMenuHome = staticCompositionLocalOf { MenuHome() }

/**
 * Lets [content] be worked from a keyboard or a game pad alone: the arrows move a cursor (focus)
 * from control to control, Enter takes the one it is on, and Escape goes back through [onBack],
 * which says whether there was anywhere to go back to.
 *
 * Compose leaves too much of this to the platform. The desktop moves focus with Tab only, never
 * with the arrows, and Android with the d-pad but not with WASD. So the keys are read here, ahead
 * of the controls, the same way on both.
 *
 * A screen puts the cursor on its first choice with [HomeCursor]; the controls show it with
 * [CursorMark]. A key handler in [modifier] reads the keys ahead of all this.
 */
@Composable
internal fun MenuKeys(
    onBack: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val inputModes = LocalInputModeManager.current
    val home = remember { MenuHome() }
    val self = remember { FocusRequester() }
    var focus by remember { mutableStateOf<FocusState?>(null) }
    // The keys whose press has gone back, so that the press's repeats and its release go nowhere:
    // held down, Escape went back a screen and then, with nowhere left to go, out of the app.
    val backKeys = remember { mutableSetOf<Key>() }
    val heldBackKeys = remember { mutableSetOf<Key>() }

    // Keys go to whatever has focus, so the menu holds it itself whenever no control does: in touch
    // mode, which a tap or a click puts the menu in and where no control can take focus, and after
    // the control under the cursor went away. The first key after either still reaches the menu, and
    // brings the cursor back. A frame's grace, because a screen that has just opened puts the cursor
    // on its first choice in the same frame, and that is where focus belongs.
    LaunchedEffect(Unit) {
        snapshotFlow { focus?.hasFocus == true }.collect { held ->
            if (held) return@collect
            withFrameNanos { }
            if (focus?.hasFocus != true) self.requestFocus()
        }
    }

    Box(
        modifier
            .onPreviewKeyEvent { event ->
                val meaning = menuKeyOf(event.key) ?: return@onPreviewKeyEvent false
                val down = event.type == KeyEventType.KeyDown
                when {
                    meaning == MenuKey.BACK -> when {
                        !down -> {
                            heldBackKeys -= event.key
                            backKeys.remove(event.key)
                        }
                        // A repeat, from a key held down.
                        event.key in heldBackKeys -> event.key in backKeys
                        else -> {
                            heldBackKeys += event.key
                            // Nowhere to go back to is left to the platform: on Android, a pad's B
                            // on the main screen leaves the app, as Back does.
                            onBack().also { if (it) backKeys += event.key }
                        }
                    }

                    // No cursor: the first key only brings it out, onto the screen's first choice.
                    // Taking anything as well would be taking something the player could not see
                    // they were on. Its release is let through, because no control saw it pressed
                    // and so none acts on it.
                    focus?.isFocused == true -> {
                        if (down) {
                            if (inputModes.inputMode == InputMode.Touch) {
                                // HomeCursor places it once the menu is out of touch mode.
                                inputModes.requestInputMode(InputMode.Keyboard)
                            } else {
                                home.requester?.requestFocus()
                            }
                        }
                        down
                    }

                    meaning == MenuKey.CONFIRM -> false

                    // Held, an arrow walks on through the controls, as it does in any menu.
                    else -> {
                        if (down) focusManager.moveFocus(meaning.direction!!)
                        true
                    }
                }
            }
            .focusRequester(self)
            .onFocusChanged { focus = it }
            // Not while a control has it. Android reads Back and Escape as leaving the control under
            // the cursor for whatever holds it, and with this there, Back only hid the cursor.
            .focusProperties { canFocus = focus?.let { it.isFocused || !it.hasFocus } ?: true }
            .focusTarget()
    ) {
        CompositionLocalProvider(LocalMenuHome provides home, content = content)
    }
}

/**
 * Makes [home] the screen's first choice: where the cursor goes whenever the screen opens to a
 * keyboard or a pad, whenever the player picks one up again after touching the screen, and whenever
 * the cursor has been lost.
 */
@Composable
internal fun HomeCursor(home: FocusRequester) {
    val menu = LocalMenuHome.current
    DisposableEffect(menu, home) {
        menu.requester = home
        onDispose { if (menu.requester === home) menu.requester = null }
    }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) {
        if (inputMode == InputMode.Keyboard) home.requestFocus()
    }
}

/** How thick the cursor's ring is. */
private val CURSOR_WIDTH = 3.dp

/**
 * The cursor on one control: whether it is there, followed by [modifier] from the control's own
 * focus, and the ring it draws around the control's edge while it is.
 *
 * Read off the focus itself rather than off the control's interactions, which reach only a listener
 * already listening: a screen's first choice takes focus as the screen opens, before its listener
 * had started, and showed no ring.
 */
@Stable
internal class CursorMark {
    /** Whether the cursor is on this control. */
    var isOn by mutableStateOf(false)
        private set

    /** Goes on the control's modifier, ahead of whatever makes the control focusable. */
    val modifier: Modifier = Modifier.onFocusChanged { isOn = it.isFocused }

    /**
     * The ring, or null while the cursor is elsewhere. In [color], which has to stand out from the
     * control and from what is around it: the theme's own primary on the menu's light screens,
     * something lighter on the title screen's dark sky.
     */
    @Composable
    fun border(color: Color = MaterialTheme.colorScheme.primary): BorderStroke? =
        if (isOn) BorderStroke(CURSOR_WIDTH, color) else null
}

/** A [CursorMark] for one control, kept across recompositions. */
@Composable
internal fun rememberCursorMark(): CursorMark = remember { CursorMark() }

/** Draws [border], the cursor, on a control of [shape] that is not a Material button or card. */
internal fun Modifier.cursorRing(border: BorderStroke?, shape: Shape): Modifier =
    if (border == null) this else border(border, shape)
