package at.smiech.cyanbat.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import at.smiech.cyanbat.AchievementStore
import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.data.ObservedAudioSettings
import at.smiech.cyanbat.data.ObservedHaptics
import at.smiech.cyanbat.data.WindowMode
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.GameText
import at.smiech.cyanbat.ui.CyanBatMenu
import at.smiech.cyanbat.ui.MenuHost
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.cyanbat.ui.rememberMenuBackStack
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.engine.DisplayMode
import at.smiech.engine.Haptics
import at.smiech.engine.InputMethod
import at.smiech.engine.impl.ControlHandler
import at.smiech.engine.impl.DesktopAudio
import at.smiech.engine.impl.GameSurface
import at.smiech.engine.impl.PadMenuKey
import at.smiech.engine.impl.PadRouter
import at.smiech.engine.impl.onComposeKeyEvent
import at.smiech.engine.impl.onComposePointerUse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.Component
import java.awt.event.KeyEvent
import kotlin.system.exitProcess

/**
 * Opens the game's window, which shows the menu or a run.
 *
 * Android splits these across two Activities; on desktop one window swaps its content, which is
 * why "back to menu" here is a state change rather than an Intent.
 *
 * The window itself is made again whenever its [WindowMode] changes (see [WindowModes]), so
 * everything that has to outlive one - the run, the menu's place and its view models, the stores
 * and the menu's music - is held out here, and the window only shows it.
 */
fun main() = application {
    // Keys are delivered to the window rather than to whatever the game is showing, and the
    // handler has to outlive a single run.
    val controls = remember { ControlHandler() }
    // Game controllers, polled for as long as the game runs; see the loop below.
    val gamepads = remember { DesktopGamepads() }
    // The window open now, which a pad's keys go to in the menu, and whether the player is at it.
    var padWindow by remember { mutableStateOf<ComposeWindow?>(null) }
    var padWindowFocused by remember { mutableStateOf(false) }
    val pads = remember {
        PadRouter(controls) { key, pressed -> padWindow?.let { pressMenuKey(it, key, pressed) } }
    }
    // The stage being played, or null while the menu is up. The window's keys go one way or the
    // other by it.
    var playingStage by remember { mutableStateOf<Int?>(null) }
    // How hard it is flown, which the player chose as it started.
    var difficulty by remember { mutableStateOf(Difficulty.DEFAULT) }
    val settings = remember { PreferencesSettingsRepository() }
    // One instance for the menu and every run, so the menu sees a stage unlock the moment the run
    // that earned it records it; see PreferencesStageUnlockStore.
    val stageUnlocks = remember { PreferencesStageUnlockStore() }
    // Shared for the same reason: the stage select shows each stage's highscore, and a run that
    // raises one has to be seen raising it.
    val highscores = remember { PreferencesHighscoreStore() }
    // Shared too: the menu shows what a run has just achieved.
    val achievements = remember { desktopAchievementStore() }
    val scope = rememberCoroutineScope()
    val audioSettings = remember(scope) { ObservedAudioSettings(settings, scope) }
    // Vibration goes to the controller in the player's hands, and nowhere while they play with the
    // keys or the mouse: the desktop has nothing else to vibrate. The player's switch applies.
    val haptics = remember(scope) {
        ObservedHaptics(
            { millis -> if (controls.inputMethod.isPad) gamepads.vibrate(millis) },
            settings,
            scope,
        )
    }
    // The menu outlives any single game instance, so it owns its own Audio.
    val menuAudio = remember {
        DesktopAudio { name ->
            object {}.javaClass.getResourceAsStream("/$name")
                ?: error("Asset $name not found on the classpath")
        }
    }
    // Out here rather than with the menu, which leaves composition for every run: a track
    // remembered there was rebuilt on each return, while the menu's ViewModel went on holding the
    // first one.
    val menuMusic = remember { menuAudio.newMusic("music/menu.wav") }
    // The menu's view models, which a window would otherwise keep, and clear as it goes: the main
    // menu's disposes the menu's music.
    val viewModels = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    val backStack = rememberMenuBackStack()
    val game = rememberRun(
        playingStage,
        difficulty,
        controls,
        highscores,
        stageUnlocks,
        achievements,
        audioSettings,
        haptics,
    ) {
        controls.releaseAll()
        playingStage = null
    }

    // The window opens in its mode: straight into it, rather than as a window that then jumps to
    // fill the screen.
    val opened = remember { settings.windowMode.value }
    val windowState = rememberWindowStateIn(opened)
    val windowModes = remember { WindowModes(windowState, opened) }
    WindowFollowsSettings(windowModes, windowState, settings)
    val fullscreenKeys = remember {
        FullscreenKeys {
            // Back and forth between a window and the full screen the player last chose.
            val next = if (settings.windowMode.value == WindowMode.WINDOWED) {
                settings.lastFullScreenMode
            } else {
                WindowMode.WINDOWED
            }
            scope.launch { settings.setWindowMode(next) }
        }
    }

    // On the window's own thread, as everything that reads the controls is. Polled whether or not
    // the player is at the window, so SDL keeps up with the pads, but heard only while they are,
    // since SDL reads a pad whatever window is in front.
    LaunchedEffect(Unit) {
        while (true) {
            val pad = gamepads.poll()
            val inGame = playingStage != null
            pads.update(pad.takeIf { padWindowFocused }, inGame, System.currentTimeMillis())
            delay(PAD_POLL_MILLIS)
        }
    }
    DisposableEffect(Unit) {
        onDispose { gamepads.dispose() }
    }

    key(windowModes.windowKey) {
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "CyanBat",
            undecorated = windowModes.applied.isFrameless(),
            // A window without a frame has an edge Compose lets the mouse drag to resize it, which
            // over the whole screen is where the mouse flies the bat to.
            resizable = windowModes.applied == WindowMode.WINDOWED,
            // Ahead of the menu and the game, so the keys work wherever the player is.
            onPreviewKeyEvent = { event ->
                // The keys a pad presses in the menu are its own, and keep its prompts.
                if (event.type == KeyEventType.KeyDown && event.awtEventOrNull !is PadKeyEvent) {
                    controls.use(InputMethod.KEYBOARD_MOUSE)
                }
                fullscreenKeys.onKeyEvent(event)
            },
            // A backstop for the game. The game surface claims focus and handles these itself;
            // this catches the moment between the surface appearing and its focus request landing.
            // Not for the menu, which reads its own keys, and to which the game's handler would
            // only have swallowed the arrows and Enter it is worked with.
            onKeyEvent = { playingStage != null && controls.onComposeKeyEvent(it) },
        ) {
            // Every size at once, rather than through Window's icon parameter; see WindowIcon. Set
            // from inside the content, which composes only after the window has applied that
            // parameter (null here, which empties the list), so nothing clears the icons
            // afterwards.
            LaunchedEffect(window) { window.setIconImages(WindowIcon.images) }
            // A shortcut's key let go of in another window never comes back up in this one.
            val windowFocused = LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(windowFocused) {
                if (!windowFocused) fullscreenKeys.releaseAll()
                padWindowFocused = windowFocused
            }
            DisposableEffect(window) {
                padWindow = window
                onDispose { if (padWindow === window) padWindow = null }
            }
            // The mouse, over the menu and the game alike, seen before either takes it.
            Box(
                Modifier.fillMaxSize().pointerInput(controls) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            controls.onComposePointerUse(event)
                        }
                    }
                }
            ) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides viewModels) {
                    if (game != null) {
                        val displayMode by settings.displayMode.collectAsState(DisplayMode.DEFAULT)
                        GameSurface(game, displayMode)
                    } else {
                        CyanBatMenu(
                            MenuHost(
                                settings = settings,
                                menuMusic = menuMusic,
                                stageUnlocks = stageUnlocks,
                                highscores = highscores,
                                achievements = achievements,
                                // Cleared on the way in as well as on the way out, so that nothing
                                // pressed before the run is waiting to act the moment it starts.
                                onStartGame = { id, chosen ->
                                    controls.releaseAll()
                                    difficulty = chosen
                                    playingStage = id
                                },
                                onExit = { exitProcess(0) },
                                inputMethod = { controls.inputMethod },
                            ),
                            backStack,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The run of [stage] on [difficulty], or null while the menu is up: made when a stage starts, and
 * disposed when the player goes back to the menu, not when the window it is shown in goes.
 *
 * @param onExitToMenu takes the player back to the menu.
 */
@Composable
private fun rememberRun(
    stage: Int?,
    difficulty: Difficulty,
    controls: ControlHandler,
    highscores: PreferencesHighscoreStore,
    stageUnlocks: PreferencesStageUnlockStore,
    achievements: AchievementStore,
    audioSettings: AudioSettings,
    haptics: Haptics,
    onExitToMenu: () -> Unit,
): DesktopGame? {
    val game = remember(stage, difficulty) {
        stage?.let { id ->
            DesktopGame(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT, controls).also { game ->
                val env = CyanBatEnvironment(
                    assets = GameAssets.load(game.graphics, game.audio),
                    // Blocking, as Compose's own string lookups are on the desktop: there is no run
                    // to show without its text, and it is one small file, read once.
                    text = runBlocking { GameText.load() },
                    haptics = haptics,
                    highscores = highscores,
                    stageUnlocks = stageUnlocks,
                    achievements = achievements,
                    onExitToMenu = onExitToMenu,
                    audioSettings = audioSettings,
                )
                game.setScreen(GameScreen(game, env, id, difficulty))
            }
        }
    }
    // A run owns its screen and its audio, and neither is needed once the player is back in the
    // menu. Android gets this from the game activity's onDestroy; here nothing else would do it, so
    // the game over track played on over the menu and every run leaked its sound lines and music
    // threads.
    DisposableEffect(game) {
        onDispose {
            game?.run {
                currentScreen?.dispose()
                currentScreen = null
                audio.dispose()
            }
        }
    }
    return game
}

/** How often the game controllers are read: oftener than the frame, so no press waits long. */
private const val PAD_POLL_MILLIS = 8L

/**
 * A key a game controller pressed in the menu: an arrow, Enter or Escape, so the menu reads the pad
 * as it reads the keyboard, and a pad's A takes whatever the cursor is on as Enter does. Its own
 * class, so the window can tell it from the keyboard's.
 */
private class PadKeyEvent(source: Component, id: Int, keyCode: Int) :
    KeyEvent(source, id, System.currentTimeMillis(), 0, keyCode, CHAR_UNDEFINED)

/** Presses [key], or lets it go, in [window]'s menu, as the keyboard would. */
private fun pressMenuKey(window: ComposeWindow, key: PadMenuKey, pressed: Boolean) {
    // Keys go to whatever in the window has focus, which in Compose's window is its canvas.
    val target = window.mostRecentFocusOwner ?: return
    val code = when (key) {
        PadMenuKey.UP -> KeyEvent.VK_UP
        PadMenuKey.DOWN -> KeyEvent.VK_DOWN
        PadMenuKey.LEFT -> KeyEvent.VK_LEFT
        PadMenuKey.RIGHT -> KeyEvent.VK_RIGHT
        PadMenuKey.CONFIRM -> KeyEvent.VK_ENTER
        PadMenuKey.BACK -> KeyEvent.VK_ESCAPE
    }
    val id = if (pressed) KeyEvent.KEY_PRESSED else KeyEvent.KEY_RELEASED
    target.dispatchEvent(PadKeyEvent(target, id, code))
}
