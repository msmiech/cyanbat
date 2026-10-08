package at.smiech.cyanbat.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import at.smiech.cyanbat.data.SettingsRepository
import at.smiech.cyanbat.data.WindowMode
import kotlinx.coroutines.launch
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/** Whether the game is running on a Mac, whose full screen is its own; see [WindowModes]. */
internal val hostIsMac: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

/** The window's size when it opens as a window, and when it goes back to one it never was. */
internal val WINDOWED_SIZE = DpSize(800.dp, 600.dp)

/**
 * The keys that take the window in and out of full screen: F11, as browsers and most desktop
 * programs take it; Alt+Enter, as games on Windows do; and on a Mac Ctrl+Cmd+F, the system's own.
 * The window reads them ahead of everything in it, so they work in the menu and in a run alike.
 *
 * A press toggles once, however long the key is held, and is swallowed whole - its repeats and its
 * release with it - so nothing behind it sees half of a key: the menu takes Enter, and the game
 * Enter and Escape.
 *
 * @param toggle takes the window in or out of full screen.
 */
internal class FullscreenKeys(
    private val isMac: Boolean = hostIsMac,
    private val toggle: () -> Unit,
) {
    /** The keys whose press toggled and has not been let go of yet. */
    private val held = mutableSetOf<Key>()

    /** @return true when the key was one of these, which stops it going any further. */
    fun onKeyEvent(event: KeyEvent): Boolean = when (event.type) {
        KeyEventType.KeyDown ->
            onPress(event.key, event.isAltPressed, event.isCtrlPressed, event.isMetaPressed)

        KeyEventType.KeyUp -> onRelease(event.key)
        else -> false
    }

    /**
     * A key going down, or repeating while it is held. Split out from [onKeyEvent], as the game's
     * own key mapping is, because a Compose key event cannot be built by hand outside a window.
     */
    internal fun onPress(
        key: Key,
        alt: Boolean = false,
        ctrl: Boolean = false,
        meta: Boolean = false,
    ): Boolean {
        if (key in held) return true
        if (!isShortcut(key, alt, ctrl, meta)) return false
        held += key
        toggle()
        return true
    }

    internal fun onRelease(key: Key): Boolean = held.remove(key)

    /**
     * Forgets the keys held. For when the window loses focus: their release goes elsewhere, and a
     * key still counted as held would take its next press for a repeat.
     */
    fun releaseAll() = held.clear()

    private fun isShortcut(key: Key, alt: Boolean, ctrl: Boolean, meta: Boolean): Boolean =
        when (key) {
            Key.F11 -> true
            Key.Enter, Key.NumPadEnter -> alt
            Key.F -> isMac && ctrl && meta
            else -> false
        }
}

/** Whether the window has no frame in [this] mode: in either full screen, except on a Mac. */
internal fun WindowMode.isFrameless(isMac: Boolean = hostIsMac): Boolean =
    this != WindowMode.WINDOWED && !isMac

/**
 * The window's state as it opens in [mode], so it opens straight into it rather than as a window
 * that then jumps: borderless over the screen it opens on, which is the main one.
 */
@Composable
internal fun rememberWindowStateIn(mode: WindowMode): WindowState = when (mode) {
    WindowMode.WINDOWED -> rememberWindowState(size = WINDOWED_SIZE)
    WindowMode.FULLSCREEN ->
        rememberWindowState(placement = WindowPlacement.Fullscreen, size = WINDOWED_SIZE)

    WindowMode.BORDERLESS -> {
        val screen = remember { defaultScreen() }
        rememberWindowState(position = screen.position, size = screen.size)
    }
}

/**
 * Takes the game's window from one [WindowMode] to another, remembering where it was as a window
 * to go back to: a window maximized before comes back maximized.
 *
 * Off a Mac, every mode is a window of its own ([windowKey]): the host makes a new window whenever
 * the mode changes, from the [state] this has set for the mode, as the game's first window is made
 * for the mode it opens in. Either full screen has no frame ([isFrameless]), because Windows' own
 * full screen leaves a framed window's title bar across the top of the screen, and a frame cannot
 * be changed on a window that is up: AWT refuses, and Compose stops drawing a window whose native
 * window was let go of and made again. Whatever has to outlive a window - a run, the menu's place
 * and its view models - is the host's, above it.
 *
 * On a Mac the frame stays, the system's own full screen takes it away, and one window does for
 * every mode: this only changes its placement, as the Mac's own green button does.
 *
 * @param opened the mode the first window opened in.
 */
@Stable
internal class WindowModes(
    private val state: WindowState,
    opened: WindowMode,
    private val isMac: Boolean = hostIsMac,
) {
    /** The mode the window is in. */
    var applied: WindowMode by mutableStateOf(opened)
        private set

    /** What the host keys its window on: a new window when this changes. */
    val windowKey: Any get() = if (isMac) Unit else applied

    /** Where the window last was as a floating window; null if it has never been one. */
    private var windowed: Pair<WindowPosition, DpSize>? = null
    private var windowedMaximized = false

    /**
     * The window moved, was resized or maximized, by the player or the system. Only a floating
     * window's bounds are kept: a maximized one goes back to the bounds it had before.
     */
    fun onWindowChanged() {
        if (applied != WindowMode.WINDOWED) return
        val position = state.position
        if (state.placement == WindowPlacement.Floating && position is WindowPosition.Absolute) {
            windowed = position to state.size
        }
    }

    fun apply(mode: WindowMode) {
        if (mode == applied) return
        if (applied == WindowMode.WINDOWED) {
            onWindowChanged()
            windowedMaximized = state.placement == WindowPlacement.Maximized
        }
        when (mode) {
            WindowMode.WINDOWED -> {
                // A Mac's window comes back from its full screen where it was by itself.
                if (!isMac) {
                    val (position, size) = windowed
                        ?: (WindowPosition(Alignment.Center) to WINDOWED_SIZE)
                    state.position = position
                    state.size = size
                }
                state.placement =
                    if (windowedMaximized && !isMac) WindowPlacement.Maximized
                    else WindowPlacement.Floating
            }

            WindowMode.FULLSCREEN -> state.placement = WindowPlacement.Fullscreen
            WindowMode.BORDERLESS -> {
                val screen = screenUnder(state)
                state.placement = WindowPlacement.Floating
                state.position = screen.position
                state.size = screen.size
            }
        }
        applied = mode
    }
}

/**
 * Keeps the window and the player's setting in step, both ways. The setting, changed in Settings
 * or by [FullscreenKeys], puts the window in its mode; and the window taken in or out of full
 * screen some other way - a Mac's green button, a window manager's own key - changes the setting,
 * so Settings shows what the window is and the next start opens it the same way.
 */
@Composable
internal fun WindowFollowsSettings(
    modes: WindowModes,
    state: WindowState,
    settings: SettingsRepository,
) {
    LaunchedEffect(modes, state, settings) {
        launch {
            snapshotFlow { Triple(state.placement, state.position, state.size) }.collect {
                modes.onWindowChanged()
                val fullscreen = state.placement == WindowPlacement.Fullscreen
                when {
                    fullscreen && modes.applied != WindowMode.FULLSCREEN ->
                        settings.setWindowMode(WindowMode.FULLSCREEN)

                    !fullscreen && modes.applied == WindowMode.FULLSCREEN ->
                        settings.setWindowMode(WindowMode.WINDOWED)
                }
            }
        }
        settings.windowMode.collect(modes::apply)
    }
}

/** A screen's bounds as a window's position and size. */
private class Screen(bounds: Rectangle) {
    val position = WindowPosition(bounds.x.dp, bounds.y.dp)
    val size = DpSize(bounds.width.dp, bounds.height.dp)
}

private fun defaultScreen() = Screen(
    GraphicsEnvironment.getLocalGraphicsEnvironment()
        .defaultScreenDevice.defaultConfiguration.bounds
)

/** The screen the middle of the window is on: the main one, or a second one it was dragged to. */
private fun screenUnder(state: WindowState): Screen {
    val position = state.position
    val size = state.size
    if (position is WindowPosition.Absolute && size.isSpecified) {
        val x = (position.x + size.width / 2).value.toInt()
        val y = (position.y + size.height / 2).value.toInt()
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            .map { it.defaultConfiguration.bounds }
            .firstOrNull { it.contains(x, y) }
            ?.let { return Screen(it) }
    }
    return defaultScreen()
}

/**
 * Whether the game is running on a Steam Deck, which Steam tells it in its environment - unless
 * the player launches it with `SteamDeck=0` to have it not know. A Deck in its gaming mode shows
 * every game across its whole screen, scaling up a window that is smaller, so there the game opens
 * in full screen and draws at the screen's own resolution.
 */
internal fun isSteamDeck(): Boolean = System.getenv("SteamDeck") == "1"
