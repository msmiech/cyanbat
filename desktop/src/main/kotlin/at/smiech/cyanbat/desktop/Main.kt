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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.data.ObservedAudioSettings
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.GameText
import at.smiech.cyanbat.ui.CyanBatMenu
import at.smiech.cyanbat.ui.MenuHost
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.engine.DisplayMode
import at.smiech.engine.Haptics
import at.smiech.engine.impl.ControlHandler
import at.smiech.engine.impl.DesktopAudio
import at.smiech.engine.impl.onComposeKeyEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/** Opens the game's one window, which shows the menu or a run. */
fun main() = application {
    // Hoisted above the window, because keys are delivered to the window rather than to whatever
    // the game is showing, and because it has to outlive a single run.
    val controls = remember { ControlHandler() }
    // The stage being played, or null while the menu is up. Out here, because the window's keys
    // below go one way or the other by it.
    var playingStage by remember { mutableStateOf<Int?>(null) }
    // Out here too, because the window opens in full screen or not by it: straight into it, rather
    // than as a window that then jumps to fill the screen.
    val settings = remember { PreferencesSettingsRepository() }
    val windowState = rememberWindowState(
        placement = if (settings.isFullscreen.value) {
            WindowPlacement.Fullscreen
        } else {
            WindowPlacement.Floating
        },
    )
    val scope = rememberCoroutineScope()
    val fullscreenKeys = remember(windowState) {
        FullscreenKeys {
            scope.launch {
                settings.setFullscreen(windowState.placement != WindowPlacement.Fullscreen)
            }
        }
    }
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "CyanBat",
        // Ahead of the menu and the game, so the keys work wherever the player is.
        onPreviewKeyEvent = fullscreenKeys::onKeyEvent,
        // A backstop for the game. The game surface claims focus and handles these itself; this
        // catches the moment between the surface appearing and its focus request landing. Not for
        // the menu, which reads its own keys, and to which the game's handler would only have
        // swallowed the arrows and Enter it is worked with.
        onKeyEvent = { playingStage != null && controls.onComposeKeyEvent(it) },
    ) {
        // Every size at once, rather than through Window's icon parameter; see WindowIcon. Set from
        // inside the content, which composes only after the window has applied that parameter
        // (null here, which empties the list), so nothing clears the icons afterwards.
        LaunchedEffect(window) { window.setIconImages(WindowIcon.images) }
        FullscreenFollowsSettings(windowState, settings)
        // A shortcut's key let go of in another window never comes back up in this one.
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(windowFocused) {
            if (!windowFocused) fullscreenKeys.releaseAll()
        }
        CyanBatApp(settings, controls, playingStage) { playingStage = it }
    }
}

/**
 * Desktop shell: the shared menu, and the game when it is running.
 *
 * Android splits these across two Activities; on desktop one window swaps its content, which is
 * why "back to menu" here is a state change rather than an Intent.
 *
 * @param playingStage the stage being played, or null while the menu is up; [play] changes it.
 */
@Composable
private fun CyanBatApp(
    settings: PreferencesSettingsRepository,
    controls: ControlHandler,
    playingStage: Int?,
    play: (stage: Int?) -> Unit,
) {
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
    val menuAudio = remember {
        DesktopAudio { name ->
            object {}.javaClass.getResourceAsStream("/$name")
                ?: error("Asset $name not found on the classpath")
        }
    }
    // Out here rather than in the menu branch, which leaves composition for every run: a track
    // remembered there was rebuilt on each return, while the menu's ViewModel went on holding the
    // first one.
    val menuMusic = remember { menuAudio.newMusic("music/menu.wav") }

    val stage = playingStage
    if (stage != null) {
        val game = remember {
            DesktopGame(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT, controls).also { game ->
                val env = CyanBatEnvironment(
                    assets = GameAssets.load(game.graphics, game.audio),
                    // Blocking, as Compose's own string lookups are on the desktop: there is no run
                    // to show without its text, and it is one small file, read once.
                    text = runBlocking { GameText.load() },
                    haptics = Haptics.None,
                    highscores = highscores,
                    stageUnlocks = stageUnlocks,
                    onExitToMenu = {
                        controls.releaseAll()
                        play(null)
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
                // Cleared on the way in as well as on the way out, so that nothing pressed before
                // the run is waiting to act the moment it starts.
                onStartGame = { id ->
                    controls.releaseAll()
                    play(id)
                },
                onExit = { exitProcess(0) },
            )
        )
    }
}
