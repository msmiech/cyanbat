package at.smiech.cyanbat.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {
    @Serializable
    data object Main : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object Credits : Screen
}

@Composable
fun MainNavGraph() {
    val backStack = rememberNavBackStack(Screen.Main as Screen)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() }
    ) { key ->
        when (key as Screen) {
            Screen.Main -> NavEntry(key) {
                MainMenuScreen(
                    onNavigateToSettings = { backStack.add(Screen.Settings) },
                    onNavigateToCredits = { backStack.add(Screen.Credits) }
                )
            }
            Screen.Settings -> NavEntry(key) { SettingsScreen() }
            Screen.Credits -> NavEntry(key) { CreditsScreen() }
        }
    }
}
