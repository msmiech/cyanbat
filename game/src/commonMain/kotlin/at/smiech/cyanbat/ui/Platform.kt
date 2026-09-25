package at.smiech.cyanbat.ui

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
