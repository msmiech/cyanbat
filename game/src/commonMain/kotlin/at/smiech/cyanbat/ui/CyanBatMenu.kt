package at.smiech.cyanbat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import at.smiech.cyanbat.LevelUnlockStore
import at.smiech.cyanbat.data.SettingsRepository
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.button_back
import at.smiech.engine.Music
import org.jetbrains.compose.resources.stringResource

/**
 * Everything the shared menu needs from its host.
 *
 * Android supplies a DataStore-backed [SettingsRepository] and starts an Activity; desktop
 * supplies a preferences-backed one and swaps the window content. Neither detail reaches the UI.
 */
class MenuHost(
    val settings: SettingsRepository,
    /** Menu track, or null where audio is unavailable. */
    val menuMusic: Music?,
    /** Which levels the player can start from. The same store the game unlocks them in. */
    val levelUnlocks: LevelUnlockStore,
    /** Start a run on the level with this 1-based id. */
    val onStartGame: (levelId: Int) -> Unit,
    val onExit: () -> Unit,
)

/**
 * The whole menu: main screen, level select, settings and credits, with a back stack.
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
        // Hoisted above the destinations, so the main screen and the level select share one - and
        // with it one menu track, which keeps playing as the player moves between them.
        val menuViewModel = viewModel {
            MainMenuViewModel(host.settings, host.menuMusic, host.levelUnlocks)
        }
        when (backStack.current) {
            MenuDestination.Main -> {
                MainMenuScreen(
                    viewModel = menuViewModel,
                    onNavigateToSettings = { backStack.navigateTo(MenuDestination.Settings) },
                    onNavigateToCredits = { backStack.navigateTo(MenuDestination.Credits) },
                    onNavigateToLevelSelect = { backStack.navigateTo(MenuDestination.LevelSelect) },
                    onStartGame = host.onStartGame,
                    onExit = host.onExit,
                )
            }

            MenuDestination.LevelSelect -> SubScreen(onBack = { backStack.back() }) {
                LevelSelectScreen(menuViewModel, onStartLevel = host.onStartGame)
            }

            MenuDestination.Settings -> SubScreen(onBack = { backStack.back() }) {
                val viewModel = viewModel { SettingsViewModel(host.settings) }
                SettingsScreen(viewModel)
            }

            MenuDestination.Credits -> SubScreen(onBack = { backStack.back() }) {
                CreditsScreen()
            }
        }
    }
}

/**
 * A screen reached from the main menu, with a way back to it where the platform has none of its
 * own. On Android the system back already does this and a second control would only crowd it.
 */
@Composable
private fun SubScreen(onBack: () -> Unit, content: @Composable () -> Unit) {
    if (hasSystemBack) {
        content()
        return
    }
    Surface {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = onBack) {
                Text("← " + stringResource(Res.string.button_back))
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}
