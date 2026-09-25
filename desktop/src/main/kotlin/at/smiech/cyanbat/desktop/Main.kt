package at.smiech.cyanbat.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.data.ObservedAudioSettings
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.ui.CyanBatMenu
import at.smiech.cyanbat.ui.MenuHost
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.engine.Haptics
import at.smiech.engine.impl.ControlHandler
import at.smiech.engine.impl.DesktopAudio
import at.smiech.engine.impl.onComposeKeyEvent
import kotlin.system.exitProcess

private const val FRAME_BUFFER_WIDTH = 480
private const val FRAME_BUFFER_HEIGHT = 320

fun main() = application {
    // The handler is hoisted above the window because keys are delivered to the window, not to
    // whatever the game happens to be showing - and because it has to outlive a single run.
    val controls = remember { ControlHandler() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "CyanBat",
        // A backstop. The game surface claims focus and handles these itself; this catches the
        // window-level case where focus sits somewhere that does not - the menu, or the moment
        // between the surface appearing and its focus request landing.
        onKeyEvent = controls::onComposeKeyEvent,
    ) {
        CyanBatApp(controls)
    }
}

/**
 * Desktop shell: the shared menu, and the game when it is running.
 *
 * Android splits these across two Activities; on desktop one window swaps its content, which is
 * why "back to menu" here is a state change rather than an Intent.
 */
@Composable
private fun CyanBatApp(controls: ControlHandler) {
    // The level being played, or null while the menu is up.
    var playingLevel by remember { mutableStateOf<Int?>(null) }
    val settings = remember { PreferencesSettingsRepository() }
    // One instance for the menu and every run, so the menu sees a level unlock the moment the run
    // that earned it records it; see PreferencesLevelUnlockStore.
    val levelUnlocks = remember { PreferencesLevelUnlockStore() }
    val scope = rememberCoroutineScope()
    val audioSettings = remember(scope) { ObservedAudioSettings(settings, scope) }
    // The menu outlives any single game instance, so it owns its own Audio.
    val menuAudio = remember { DesktopAudio { name ->
        object {}.javaClass.getResourceAsStream("/$name") ?: error("Asset <$name> not found")
    } }
    // Out here rather than in the menu branch, which leaves composition for every run: a track
    // remembered there was rebuilt on each return, while the menu's ViewModel went on holding the
    // first one.
    val menuMusic = remember { menuAudio.newMusic("menu_theme.mp3") }

    val level = playingLevel
    if (level != null) {
        val game = remember {
            DesktopGame(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT, controls).also { game ->
                val env = CyanBatEnvironment(
                    assets = GameAssets.load(game.graphics, game.audio),
                    haptics = Haptics.None,
                    highscores = PreferencesHighscoreStore(),
                    levelUnlocks = levelUnlocks,
                    onExitToMenu = {
                        controls.releaseAll()
                        playingLevel = null
                    },
                    audioSettings = audioSettings,
                )
                game.setScreen(GameScreen(game, env, level))
            }
        }
        // A run owns its screen and its audio, and neither is needed once the player is back in
        // the menu. Android gets this from the game activity's onDestroy; here nothing else would
        // do it, so the game over track played on over the menu and every run leaked its sound
        // lines and music threads.
        DisposableEffect(game) {
            onDispose {
                game.currentScreen?.dispose()
                game.currentScreen = null
                game.audio.dispose()
            }
        }
        GameSurface(game)
    } else {
        CyanBatMenu(
            MenuHost(
                settings = settings,
                menuMusic = menuMusic,
                levelUnlocks = levelUnlocks,
                // The handler spans the menu too, so an Escape pressed here would otherwise be
                // waiting to pause the run the moment it starts.
                onStartGame = { id ->
                    controls.releaseAll()
                    playingLevel = id
                },
                onExit = { exitProcess(0) },
            )
        )
    }
}
