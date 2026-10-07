package at.smiech.cyanbat.ui

import androidx.compose.runtime.Composable
import at.smiech.cyanbat.data.WindowMode
import org.jetbrains.compose.resources.StringResource

/**
 * The help dialog's controls section, which is the one part of it that differs by platform:
 * a phone is flown by touch and paused with Back, a desktop by mouse or keys and paused with Esc.
 */
internal expect val helpControls: StringResource

/**
 * Whether the platform gives the player a way back out of a menu screen on its own. Android has
 * the back button and gesture; a desktop window has nothing, so the menu has to draw a button.
 */
internal expect val hasSystemBack: Boolean

/**
 * Whether the platform has anything to vibrate, and so whether Settings offers to turn vibration
 * off. A phone does; the desktop reads no game controllers yet, so it has nothing to rumble.
 */
internal expect val canVibrate: Boolean

/**
 * The ways the game can take its screen that Settings offers, or none where it always fills it, as
 * a phone's does. A desktop has a window to leave; a Mac's own full screen is already borderless,
 * and a window without a frame would only sit under its menu bar and Dock.
 */
internal expect val windowModes: List<WindowMode>

/**
 * Sets the system's status and navigation bar icons to read against what the menu draws under them:
 * light over a dark screen, dark over a light one. Android draws the menu edge to edge, under its
 * bars; the desktop's window has none over it.
 */
@Composable
internal expect fun SystemBarIcons(overDark: Boolean)
