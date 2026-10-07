package at.smiech.cyanbat.ui

import androidx.compose.runtime.Composable
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
 * Whether the game is played in a window the player can have fill the screen, and so whether
 * Settings offers full screen. The desktop's is; a phone's game always fills its screen.
 */
internal expect val canGoFullscreen: Boolean

/**
 * Sets the system's status and navigation bar icons to read against what the menu draws under them:
 * light over a dark screen, dark over a light one. Android draws the menu edge to edge, under its
 * bars; the desktop's window has none over it.
 */
@Composable
internal expect fun SystemBarIcons(overDark: Boolean)
