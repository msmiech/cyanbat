package at.smiech.cyanbat.ui

import androidx.compose.runtime.Composable
import at.smiech.cyanbat.data.WindowMode
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.dialog_help_controls_ios
import org.jetbrains.compose.resources.StringResource
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPhone

// Flown by touch, as on Android, but paused with the run's own button, since iOS has no Back.
internal actual val helpControls: StringResource = Res.string.dialog_help_controls_ios

// No back button, and the edge swipe is not something a player finds unprompted, so the menu draws
// its own Back buttons, as it does on the desktop.
internal actual val hasSystemBack: Boolean = false

// An iPhone vibrates; an iPad has nothing to vibrate, so its Settings leave the switch out.
internal actual val canVibrate: Boolean =
    UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPhone

// An iPhone or an iPad is flown by touch, as a phone is on Android.
internal actual val hasTouchScreen: Boolean = true

// The game fills its screen, or on an iPad its window, which the system sizes.
internal actual val windowModes: List<WindowMode> = emptyList()

// The status bar is hidden by the app's view controller, menu and run alike (see iosApp).
@Composable
internal actual fun SystemBarIcons(overDark: Boolean) = Unit
