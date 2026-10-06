package at.smiech.cyanbat.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.dialog_help_controls_touch
import org.jetbrains.compose.resources.StringResource

internal actual val helpControls: StringResource = Res.string.dialog_help_controls_touch

internal actual val hasSystemBack: Boolean = true

internal actual val canVibrate: Boolean = true

// The window's theme alone would leave the icons light on every screen, and so white on white over
// the light theme's settings.
@Composable
internal actual fun SystemBarIcons(overDark: Boolean) {
    val view = LocalView.current
    val window = view.context.findActivity()?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = !overDark
            isAppearanceLightNavigationBars = !overDark
        }
    }
}

/** The activity this context belongs to, through any wrappers around it. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
