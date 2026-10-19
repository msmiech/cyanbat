package at.smiech.cyanbat.ui

import androidx.compose.runtime.Composable
import at.smiech.cyanbat.data.WindowMode
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.dialog_help_controls_web
import kotlinx.browser.window
import org.jetbrains.compose.resources.StringResource

// Flown as on the desktop, or by touch on a phone's browser. Full screen is the browser's own.
internal actual val helpControls: StringResource = Res.string.dialog_help_controls_web

// The browser's Back leaves the page rather than going back a screen, so the menu draws its own.
internal actual val hasSystemBack: Boolean = false

// Only a phone's browser could vibrate, and the page gives the game no haptics yet.
internal actual val canVibrate: Boolean = false

// A phone's or a tablet's browser, or a laptop's with a touch screen; not one with only a mouse.
// maxTouchPoints is how many fingers the screen can follow at once: none without a touch screen.
internal actual val hasTouchScreen: Boolean = window.navigator.maxTouchPoints > 0

// The page fills the browser's window, and the browser's own full screen takes it from there.
internal actual val windowModes: List<WindowMode> = emptyList()

// The browser draws its own bars, in its own theme.
@Composable
internal actual fun SystemBarIcons(overDark: Boolean) = Unit
