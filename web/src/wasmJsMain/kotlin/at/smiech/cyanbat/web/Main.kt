package at.smiech.cyanbat.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.data.ObservedAudioSettings
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
import at.smiech.engine.impl.ComposeGame
import at.smiech.engine.impl.ComposeGraphics
import at.smiech.engine.impl.ControlHandler
import at.smiech.engine.impl.GameSurface
import at.smiech.engine.impl.WebAssets
import at.smiech.engine.impl.WebAudio
import at.smiech.engine.impl.WebAudioDevice
import kotlinx.browser.document
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import org.jetbrains.skia.Image

/**
 * Opens the game in the page, which shows the menu or a run: the browser's counterpart of the
 * desktop's window, which swaps its content the same way.
 *
 * The page needs no files of the game's to show the menu, so it shows it at once, and fetches the
 * pictures and the sounds a run is built from meanwhile ([WebAssets]); starting a run waits for
 * them if they are not in yet.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val assets = WebAssets()
    val preloaded = MainScope().async {
        // Everything but the music, which is most of the download and is fetched as it is played.
        val files = assets.text("index.txt").lines()
            .filter { it.isNotBlank() && !it.startsWith("music/") }
        assets.preload(files)
    }
    val sound = WebAudioDevice()
    val focus = PageFocus()
    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!) {
        CyanBatPage(assets, preloaded, sound, focus)
    }
}

/** The menu, or the run of the stage being played. */
@Composable
private fun CyanBatPage(
    assets: WebAssets,
    preloaded: Deferred<Unit>,
    sound: WebAudioDevice,
    focus: PageFocus,
) {
    // Keys reach the run through the game surface, but the handler has to outlive a single run.
    val controls = remember { ControlHandler() }
    // The stage being played, or null while the menu is up.
    var playingStage by remember { mutableStateOf<Int?>(null) }
    val storage = remember { WebStorage() }
    val settings = remember { WebSettingsRepository(storage) }
    // One of each for the menu and every run, so the menu sees what a run records the moment it
    // records it; see WebStores.
    val stageUnlocks = remember { WebStageUnlockStore(storage) }
    val highscores = remember { WebHighscoreStore(storage) }
    val scope = rememberCoroutineScope()
    val audioSettings = remember(scope) { ObservedAudioSettings(settings, scope) }
    // The menu outlives any single run, so it owns its own Audio, and its track is made out here
    // with it rather than with the menu, which leaves composition for every run.
    val menuMusic = remember { WebAudio(sound, assets).newMusic("music/menu.wav") }
    // The menu's view models, kept out here for the same reason.
    val viewModels = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    val backStack = rememberMenuBackStack()
    val stage = playingStage
    val run = rememberRun(
        stage,
        assets,
        preloaded,
        sound,
        controls,
        highscores,
        stageUnlocks,
        audioSettings,
    ) {
        controls.releaseAll()
        playingStage = null
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides viewModels) {
        when {
            run != null -> {
                val displayMode by settings.displayMode.collectAsState(DisplayMode.DEFAULT)
                GameSurface(run, displayMode, focus.isFocused)
            }

            // A run on its way: the pictures it is built from are still coming.
            stage != null -> Box(Modifier.fillMaxSize().background(Color.Black))

            else -> CyanBatMenu(
                MenuHost(
                    settings = settings,
                    menuMusic = menuMusic,
                    stageUnlocks = stageUnlocks,
                    highscores = highscores,
                    // Cleared on the way in as well as on the way out, so that nothing pressed
                    // before the run is waiting to act the moment it starts.
                    onStartGame = { id ->
                        controls.releaseAll()
                        playingStage = id
                    },
                    // A page cannot close its own tab.
                    onExit = null,
                ),
                backStack,
            )
        }
    }
}

/**
 * The run of [stage], or null while the menu is up or the run is still being built: made once the
 * pictures and sounds are in, and the run's text read, and disposed when the player goes back to
 * the menu.
 *
 * @param onExitToMenu takes the player back to the menu.
 */
@Composable
private fun rememberRun(
    stage: Int?,
    assets: WebAssets,
    preloaded: Deferred<Unit>,
    sound: WebAudioDevice,
    controls: ControlHandler,
    highscores: WebHighscoreStore,
    stageUnlocks: WebStageUnlockStore,
    audioSettings: AudioSettings,
    onExitToMenu: () -> Unit,
): ComposeGame? {
    val fonts = LocalFontFamilyResolver.current
    val run by produceState<ComposeGame?>(null, stage) {
        value = null
        val id = stage ?: return@produceState
        try {
            preloaded.await()
        } catch (exc: Exception) {
            // Offline, or the server lacks a file: there is no run without its pictures.
            println("CyanBat: the game's files could not be fetched - $exc")
            onExitToMenu()
            return@produceState
        }
        val game = ComposeGame(
            FRAME_BUFFER_WIDTH,
            FRAME_BUFFER_HEIGHT,
            ComposeGraphics(
                FRAME_BUFFER_WIDTH,
                FRAME_BUFFER_HEIGHT,
                { name -> loadImage(assets, name) },
                fonts,
            ),
            WebAudio(sound, assets),
            controls,
        )
        val env = CyanBatEnvironment(
            assets = GameAssets.load(game.graphics, game.audio),
            text = GameText.load(),
            haptics = Haptics.None,
            highscores = highscores,
            stageUnlocks = stageUnlocks,
            onExitToMenu = onExitToMenu,
            audioSettings = audioSettings,
        )
        game.setScreen(GameScreen(game, env, id))
        value = game
        // A run owns its screen and its audio, and neither is needed once the player is back in
        // the menu.
        awaitDispose {
            value = null
            game.currentScreen?.dispose()
            game.currentScreen = null
            game.audio.dispose()
        }
    }
    return run
}

/**
 * An asset decoded by Skia and marked immutable, for speed, as on the desktop: Compose's canvas
 * copies a bitmap that could still change every time it draws it.
 */
private fun loadImage(assets: WebAssets, name: String): ImageBitmap =
    Image.makeFromEncoded(assets.bytes(name)).toComposeImageBitmap()
        .also { it.asSkiaBitmap().setImmutable() }
