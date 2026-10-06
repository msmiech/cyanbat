package at.smiech.cyanbat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.AppLanguage
import at.smiech.cyanbat.data.SettingsRepository
import at.smiech.cyanbat.data.ThemeMode
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
    /** Which stages the player can start from. The same store the game unlocks them in. */
    val stageUnlocks: StageUnlockStore,
    /** Each stage's highscore, for the stage select. The same store the game saves them in. */
    val highscores: HighscoreStore,
    /** Start a run on the stage with this 1-based id. */
    val onStartGame: (stageId: Int) -> Unit,
    val onExit: () -> Unit,
)

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

/**
 * The whole menu: main screen, stage select, settings and credits, with a back stack.
 *
 * Shared by both platforms - this is the entry point Android's MainActivity and the desktop
 * window each render. It is drawn light or dark by the player's [ThemeMode], and worked by touch,
 * by mouse, or by a keyboard or a game pad alone; see [MenuKeys].
 */
@Composable
fun CyanBatMenu(
    host: MenuHost,
    /** Pass a host-owned stack to route a platform back gesture into [MenuBackStack.back]. */
    backStack: MenuBackStack = rememberMenuBackStack(),
) {
    // The default until the store is read, a frame or two in, rather than a blank frame: the menu
    // opens on the main screen, whose sky is the same in either theme, so at worst its buttons
    // change color, and only for a player who chose against the system.
    val themeMode by host.settings.themeMode.collectAsState(ThemeMode.DEFAULT)
    val dark = themeMode.isDark(isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        // The main screen draws its night sky in either theme, so the bars over it stay light.
        SystemBarIcons(overDark = dark || backStack.current == MenuDestination.Main)
        // Hoisted above the destinations, so the main screen and the stage select share one - and
        // with it one menu track, which keeps playing as the player moves between them.
        val menuViewModel = viewModel {
            MainMenuViewModel(host.settings, host.menuMusic, host.stageUnlocks, host.highscores)
        }
        // Every string is read again when the language changes, which nothing else would make a
        // string read again: Compose's resources pick the language when a string is first read.
        // The host has put it into effect by the time it tells the menu of it, and the back stack
        // is out here, so the player is still in Settings, where they chose it.
        val language by host.settings.language.collectAsState(AppLanguage.DEFAULT)
        key(language) {
            // Keeps what a screen remembers while the player is in another one, so the main
            // screen's cursor is still on Settings when they come back out of it.
            val screens = rememberSaveableStateHolder()
            MenuKeys(onBack = backStack::back) {
                screens.SaveableStateProvider(backStack.current.name) {
                    Destination(backStack, host, menuViewModel)
                }
            }
        }
    }
}

@Composable
private fun Destination(
    backStack: MenuBackStack,
    host: MenuHost,
    menuViewModel: MainMenuViewModel
) {
    when (backStack.current) {
        MenuDestination.Main -> {
            MainMenuScreen(
                viewModel = menuViewModel,
                onNavigateToSettings = { backStack.navigateTo(MenuDestination.Settings) },
                onNavigateToCredits = { backStack.navigateTo(MenuDestination.Credits) },
                onNavigateToStageSelect = { backStack.navigateTo(MenuDestination.StageSelect) },
                onStartGame = host.onStartGame,
                onExit = host.onExit,
            )
        }

        MenuDestination.StageSelect -> SubScreen(onBack = { backStack.back() }) {
            StageSelectScreen(menuViewModel, onStartStage = host.onStartGame)
        }

        MenuDestination.Settings -> SubScreen(onBack = { backStack.back() }) {
            val viewModel = viewModel { SettingsViewModel(host.settings) }
            SettingsScreen(viewModel)
        }

        // Nothing on it to choose, so the cursor starts on the way back out, where there is one.
        MenuDestination.Credits -> SubScreen(onBack = { backStack.back() }, backIsHome = true) {
            CreditsScreen()
        }
    }
}

/**
 * A screen reached from the main menu, with a way back to it where the platform has none of its
 * own. On Android the system back already does this and a second control would only crowd it.
 *
 * @param backIsHome whether the cursor starts on the back button, for a screen with nothing else
 *   on it to choose; see [HomeCursor].
 */
@Composable
private fun SubScreen(
    onBack: () -> Unit,
    backIsHome: Boolean = false,
    content: @Composable () -> Unit
) {
    if (hasSystemBack) {
        content()
        return
    }
    val back = remember { FocusRequester() }
    if (backIsHome) HomeCursor(back)
    val cursor = rememberCursorMark()
    Surface {
        Column(Modifier.fillMaxSize()) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.focusRequester(back).then(cursor.modifier),
                border = cursor.border(),
            ) {
                Text("← " + stringResource(Res.string.button_back))
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}
