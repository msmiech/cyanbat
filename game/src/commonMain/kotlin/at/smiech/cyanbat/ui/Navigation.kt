package at.smiech.cyanbat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

/** The menu's screens. */
enum class MenuDestination { Main, StageSelect, Settings, Credits }

/**
 * A back stack for the menu's handful of screens.
 *
 * Hand-rolled rather than Navigation3: JetBrains publishes only navigation3-ui for multiplatform,
 * not navigation3-runtime, where NavKey, NavEntry and the back stack live. For four destinations a
 * list is smaller than the dependency.
 */
class MenuBackStack(initial: MenuDestination = MenuDestination.Main) {
    private val entries = mutableStateListOf(initial)

    /** The screen on top. */
    val current: MenuDestination get() = entries.last()

    /** Whether there is a screen under the current one. */
    val canGoBack: Boolean get() = entries.size > 1

    /** Opens [destination] on top, unless it is already there. */
    fun navigateTo(destination: MenuDestination) {
        if (entries.last() != destination) entries.add(destination)
    }

    /** Closes the current screen; false, doing nothing, when it is the last. */
    fun back(): Boolean {
        if (!canGoBack) return false
        entries.removeAt(entries.lastIndex)
        return true
    }
}

/** A [MenuBackStack] kept across recompositions, starting on the main screen. */
@Composable
fun rememberMenuBackStack(): MenuBackStack = remember { MenuBackStack() }
