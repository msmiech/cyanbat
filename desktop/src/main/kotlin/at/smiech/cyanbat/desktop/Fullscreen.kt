package at.smiech.cyanbat.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import at.smiech.cyanbat.data.SettingsRepository
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

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
    private val isMac: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac"),
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

/**
 * Keeps the window and the player's setting in step, both ways. The setting, changed in Settings
 * or by [FullscreenKeys], takes the window in or out of full screen; and the window taken in or out
 * some other way - a Mac's green button, a window manager's own key - changes the setting, so
 * Settings shows what the window is and the next start opens it the same way.
 */
@Composable
internal fun FrameWindowScope.FullscreenFollowsSettings(
    state: WindowState,
    settings: SettingsRepository,
) {
    LaunchedEffect(window, state, settings) {
        // What the window was before it filled the screen, to go back to: a window maximized
        // before comes back maximized.
        var windowed = state.placement.takeUnless { it == WindowPlacement.Fullscreen }
            ?: WindowPlacement.Floating
        launch {
            snapshotFlow { state.placement }.collect {
                if (it != WindowPlacement.Fullscreen) windowed = it
            }
        }
        launch {
            // Not the first: that is the window as it opened, which is what the setting says. Kept,
            // it would store a default as though the player had chosen it.
            snapshotFlow { state.placement == WindowPlacement.Fullscreen }
                .drop(1)
                .collect { settings.setFullscreen(it) }
        }
        settings.isFullscreen.collect { fullscreen ->
            when {
                fullscreen == (state.placement == WindowPlacement.Fullscreen) -> Unit
                fullscreen -> state.placement = WindowPlacement.Fullscreen
                else -> {
                    // On the window itself, in one go, and the state hears of it from the window.
                    // Compose maximizes a window without taking it out of full screen, so one going
                    // back to maximized has to come out floating first, and through the state the
                    // two steps would collapse into the second.
                    window.placement = WindowPlacement.Floating
                    if (windowed == WindowPlacement.Maximized) {
                        window.placement = WindowPlacement.Maximized
                    }
                }
            }
        }
    }
}

/**
 * Whether the game is running on a Steam Deck, which Steam tells it in its environment - unless
 * the player launches it with `SteamDeck=0` to have it not know. A Deck in its gaming mode shows
 * every game across its whole screen, scaling up a window that is smaller, so there the game opens
 * in full screen and draws at the screen's own resolution.
 */
internal fun isSteamDeck(): Boolean = System.getenv("SteamDeck") == "1"
