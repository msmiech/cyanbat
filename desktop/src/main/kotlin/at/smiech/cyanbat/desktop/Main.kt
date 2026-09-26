package at.smiech.cyanbat.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import at.smiech.engine.DisplayMode
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
        // Every size at once, rather than through Window's icon parameter; see WindowIcon. Set from
        // inside the content, which only composes once the window has applied that parameter -
        // null here, which empties the list - so nothing clears the icons after this.
        LaunchedEffect(window) { window.setIconImages(WindowIcon.images) }
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
    // The stage being played, or null while the menu is up.
    var playingStage by remember { mutableStateOf<Int?>(null) }
    val settings = remember { PreferencesSettingsRepository() }
    val displayMode by settings.displayMode.collectAsState(DisplayMode.DEFAULT)
    // One instance for the menu and every run, so the menu sees a stage unlock the moment the run
    // that earned it records it; see PreferencesStageUnlockStore.
    val stageUnlocks = remember { PreferencesStageUnlockStore() }
    // Shared for the same reason: the stage select shows each stage's highscore, and a run that
    // raises one has to be seen raising it.
    val highscores = remember { PreferencesHighscoreStore() }
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

    val stage = playingStage
    if (stage != null) {
        val game = remember {
            DesktopGame(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT, controls).also { game ->
                val env = CyanBatEnvironment(
                    assets = GameAssets.load(game.graphics, game.audio),
                    haptics = Haptics.None,
                    highscores = highscores,
                    stageUnlocks = stageUnlocks,
                    onExitToMenu = {
                        controls.releaseAll()
                        playingStage = null
                    },
                    audioSettings = audioSettings,
                )
                game.setScreen(GameScreen(game, env, stage))
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
        GameSurface(game, displayMode)
    } else {
        CyanBatMenu(
            MenuHost(
                settings = settings,
                menuMusic = menuMusic,
                stageUnlocks = stageUnlocks,
                highscores = highscores,
                // The handler spans the menu too, so an Escape pressed here would otherwise be
                // waiting to pause the run the moment it starts.
                onStartGame = { id ->
                    controls.releaseAll()
                    playingStage = id
                },
                onExit = { exitProcess(0) },
            )
        )
    }
}
