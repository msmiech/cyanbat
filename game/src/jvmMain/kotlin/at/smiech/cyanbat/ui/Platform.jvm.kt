package at.smiech.cyanbat.ui

import androidx.compose.runtime.Composable
import at.smiech.cyanbat.data.WindowMode
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.dialog_help_controls_desktop
import org.jetbrains.compose.resources.StringResource

internal actual val helpControls: StringResource = Res.string.dialog_help_controls_desktop

internal actual val hasSystemBack: Boolean = false

internal actual val canVibrate: Boolean = false

internal actual val windowModes: List<WindowMode> =
    if (System.getProperty("os.name").orEmpty().startsWith("Mac")) {
        listOf(WindowMode.WINDOWED, WindowMode.FULLSCREEN)
    } else {
        WindowMode.entries
    }

// The window's title bar is the OS's, and follows the OS's theme.
@Composable
internal actual fun SystemBarIcons(overDark: Boolean) = Unit
