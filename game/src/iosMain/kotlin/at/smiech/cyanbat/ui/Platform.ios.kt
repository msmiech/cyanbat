package at.smiech.cyanbat.ui

import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.dialog_help_controls_touch
import org.jetbrains.compose.resources.StringResource

// Flown by touch, as on Android, but the text's pause line does not hold here: it pauses and quits
// with Back, which iOS does not have. iOS needs its own text once the game has an on-screen pause.
internal actual val helpControls: StringResource = Res.string.dialog_help_controls_touch

// No back button, and the edge swipe is not something a player finds unprompted, so the menu draws
// its own Back buttons, as it does on the desktop.
internal actual val hasSystemBack: Boolean = false
