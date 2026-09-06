package at.smiech.cyanbat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

/** The menu destinations. */
enum class MenuDestination { Main, Settings, Credits }

/**
 * A back stack for three screens.
 *
 * Deliberately hand-rolled rather than using Navigation3: JetBrains publishes only
 * navigation3-ui for multiplatform, not navigation3-runtime, which is where NavKey/NavEntry and
 * the back stack live. For three destinations a list is smaller than the dependency would be.
 */
class MenuBackStack(initial: MenuDestination = MenuDestination.Main) {
    private val entries = mutableStateListOf(initial)

    val current: MenuDestination get() = entries.last()
    val canGoBack: Boolean get() = entries.size > 1

    fun navigateTo(destination: MenuDestination) {
        if (entries.last() != destination) entries.add(destination)
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        entries.removeAt(entries.lastIndex)
        return true
    }
}

@Composable
fun rememberMenuBackStack(): MenuBackStack = remember { MenuBackStack() }
