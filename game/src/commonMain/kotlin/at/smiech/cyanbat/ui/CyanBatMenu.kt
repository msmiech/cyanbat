package at.smiech.cyanbat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import at.smiech.cyanbat.data.SettingsRepository
import at.smiech.engine.Music

/**
 * Everything the shared menu needs from its host.
 *
 * Android supplies a DataStore-backed [SettingsRepository] and starts an Activity; desktop
 * supplies a preferences-backed one and swaps the window content. Neither detail reaches the UI.
 */
class MenuHost(
    val settings: SettingsRepository,
    /** Menu track, or null where audio is unavailable (desktop, until MP3 decoding lands). */
    val menuMusic: Music?,
    val onStartGame: () -> Unit,
    val onExit: () -> Unit,
)

/**
 * The whole menu: main screen, settings and credits, with a back stack.
 *
 * Shared by both platforms - this is the entry point Android's MainActivity and the desktop
 * window each render.
 */
@Composable
fun CyanBatMenu(
    host: MenuHost,
    /** Pass a host-owned stack to route a platform back gesture into [MenuBackStack.back]. */
    backStack: MenuBackStack = rememberMenuBackStack(),
) {
    MaterialTheme {
        when (backStack.current) {
            MenuDestination.Main -> {
                val viewModel = viewModel { MainMenuViewModel(host.settings, host.menuMusic) }
                MainMenuScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { backStack.navigateTo(MenuDestination.Settings) },
                    onNavigateToCredits = { backStack.navigateTo(MenuDestination.Credits) },
                    onStartGame = host.onStartGame,
                    onExit = host.onExit,
                )
            }

            MenuDestination.Settings -> {
                val viewModel = viewModel { SettingsViewModel(host.settings) }
                SettingsScreen(viewModel)
            }

            MenuDestination.Credits -> CreditsScreen()
        }
    }
}
