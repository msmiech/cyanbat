package at.smiech.cyanbat.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.awt.ComposeWindow
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
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import at.smiech.cyanbat.data.SettingsRepository
import at.smiech.cyanbat.data.WindowMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val screen = remember {
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration.bounds
        }
        rememberWindowState(
            position = WindowPosition(screen.x.dp, screen.y.dp),
            size = DpSize(screen.width.dp, screen.height.dp),
        )
    }
}

/**
 * Takes a window from one [WindowMode] to another, remembering where it was as a window to go back
 * to: a window maximized before comes back maximized.
 *
 * Works on the AWT window itself, there and then, and Compose's [WindowState] hears of it from the
 * window. Through the state, Compose takes a change up a frame later, and some of these are two
 * steps: Compose maximizes a window without taking it out of full screen, so it has to come out
 * floating first, and through the state the two steps collapsed into the second.
 *
 * Either full screen takes the window's frame away ([isFrameless]): Windows' own full screen leaves
 * a framed window's title bar across the top of the screen. AWT takes a frame away or gives it
 * back only while there is no native window behind it, so the native window is let go of and made
 * again. Compose and skiko carry the scene across, so a run or a menu screen goes on where it was.
 *
 * @param applied the mode the window opened in.
 */
internal class WindowModes(
    private val window: ComposeWindow,
    applied: WindowMode,
    private val isMac: Boolean = hostIsMac,
) {
    /** The mode the window is in. */
    var applied: WindowMode = applied
        private set

    /** Where the window last was as a floating window; null if it has never been one. */
    private var windowedBounds: Rectangle? = null
    private var windowedMaximized = false

    /**
     * The window moved or was resized, by the player or the system. Only a floating window's
     * bounds are kept: a maximized one goes back to the bounds it had before it was maximized.
     */
    fun onWindowChanged() {
        if (applied == WindowMode.WINDOWED) rememberWindowed()
    }

    fun apply(mode: WindowMode) {
        if (mode == applied) return
        if (applied == WindowMode.WINDOWED) {
            rememberWindowed()
            windowedMaximized = window.placement == WindowPlacement.Maximized
        }
        // A Mac's green button or a window manager's key has already put it there.
        val alreadyThere =
            mode == WindowMode.FULLSCREEN && window.placement == WindowPlacement.Fullscreen
        if (!alreadyThere) {
            if (window.placement != WindowPlacement.Floating) {
                window.placement = WindowPlacement.Floating
            }
            setFrameless(mode.isFrameless(isMac))
            when (mode) {
                WindowMode.WINDOWED -> {
                    window.bounds = windowedBounds ?: centered(WINDOWED_SIZE)
                    if (windowedMaximized) window.placement = WindowPlacement.Maximized
                }

                WindowMode.FULLSCREEN -> window.placement = WindowPlacement.Fullscreen
                // The screen the window is on, which a window dragged to a second one has made
                // that one.
                WindowMode.BORDERLESS -> window.bounds = window.graphicsConfiguration.bounds
            }
        }
        applied = mode
    }

    private fun rememberWindowed() {
        if (window.placement == WindowPlacement.Floating) windowedBounds = Rectangle(window.bounds)
    }

    private fun setFrameless(frameless: Boolean) {
        if (window.isUndecorated == frameless) return
        window.isVisible = false
        window.removeNotify()
        window.isUndecorated = frameless
        window.isVisible = true
    }

    private fun centered(size: DpSize): Rectangle {
        val screen = window.graphicsConfiguration.bounds
        val width = size.width.value.toInt()
        val height = size.height.value.toInt()
        return Rectangle(
            screen.x + (screen.width - width) / 2,
            screen.y + (screen.height - height) / 2,
            width,
            height,
        )
    }
}

/**
 * Keeps the window and the player's setting in step, both ways. The setting, changed in Settings
 * or by [FullscreenKeys], puts the window in its mode; and the window taken in or out of full
 * screen some other way - a Mac's green button, a window manager's own key - changes the setting,
 * so Settings shows what the window is and the next start opens it the same way.
 *
 * @param opened the mode the window opened in.
 */
@Composable
internal fun FrameWindowScope.WindowFollowsSettings(
    state: WindowState,
    settings: SettingsRepository,
    opened: WindowMode,
) {
    // On Swing's own event queue rather than Compose's dispatcher, which runs a coroutine in the
    // middle of a frame: a native window let go of and made again there has Compose attach the
    // scene and run a frame inside that frame, which it does not allow, and skiko crashed the JVM.
    LaunchedEffect(window, state, settings) {
        withContext(Dispatchers.Main) { follow(window, state, settings, opened) }
    }
}

private suspend fun follow(
    window: ComposeWindow,
    state: WindowState,
    settings: SettingsRepository,
    opened: WindowMode,
) {
    coroutineScope {
        val modes = WindowModes(window, opened)
        launch {
            snapshotFlow { Triple(state.placement, state.position, state.size) }.collect {
                modes.onWindowChanged()
                val fullscreen = window.placement == WindowPlacement.Fullscreen
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

/**
 * Whether the game is running on a Steam Deck, which Steam tells it in its environment - unless
 * the player launches it with `SteamDeck=0` to have it not know. A Deck in its gaming mode shows
 * every game across its whole screen, scaling up a window that is smaller, so there the game opens
 * in full screen and draws at the screen's own resolution.
 */
internal fun isSteamDeck(): Boolean = System.getenv("SteamDeck") == "1"
