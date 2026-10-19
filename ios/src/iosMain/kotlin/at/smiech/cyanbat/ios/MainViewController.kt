package at.smiech.cyanbat.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.data.KeyValueAchievementStore
import at.smiech.cyanbat.data.KeyValueHighscoreStore
import at.smiech.cyanbat.data.KeyValueSettingsRepository
import at.smiech.cyanbat.data.KeyValueStageUnlockStore
import at.smiech.cyanbat.data.ObservedAudioSettings
import at.smiech.cyanbat.data.ObservedHaptics
import at.smiech.cyanbat.data.ObservedSteeringSettings
import at.smiech.cyanbat.data.SteeringSettings
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
import at.smiech.engine.impl.ComposeGame
import at.smiech.engine.impl.ComposeGraphics
import at.smiech.engine.impl.ControlHandler
import at.smiech.engine.impl.GameSurface
import at.smiech.engine.impl.IosAssets
import at.smiech.engine.impl.IosAudio
import at.smiech.engine.impl.IosAudioDevice
import at.smiech.engine.impl.IosHaptics
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.UIKit.UIViewController

/**
 * The game's one view controller, which shows the menu or a run: the iPhone's and the iPad's
 * counterpart of the desktop's window and the browser's page, which swap their content the same
 * way. The Swift app in iosApp/ puts it on screen, inside a controller of its own that hides the
 * status bar and holds off the system's edge gestures while a run is up.
 *
 * Call it once, on the main thread.
 */
@Suppress("FunctionName", "unused") // Called from Swift, by this name.
fun MainViewController(): UIViewController {
    val assets = IosAssets()
    val sound = IosAudioDevice()
    val focus = AppFocus()
    return ComposeUIViewController {
        CyanBatApp(assets, sound, focus)
    }
}

/** The menu, or the run of the stage being played. */
@Composable
private fun CyanBatApp(assets: IosAssets, sound: IosAudioDevice, focus: AppFocus) {
    // Keys reach the run through the game surface, but the handler has to outlive a single run: an
    // iPad's keyboard flies the bat as a desktop's does.
    val controls = remember { ControlHandler(InputMethod.TOUCH) }
    // The stage being played, or null while the menu is up.
    var playingStage by remember { mutableStateOf<Int?>(null) }
    // How hard it is flown, which the player chose as it started.
    var difficulty by remember { mutableStateOf(Difficulty.DEFAULT) }
    val storage = remember { UserDefaultsStorage() }
    // The language is iOS's own per-app setting, which the system's Settings app changes too, so
    // it is read from there rather than from the store.
    val settings = remember {
        KeyValueSettingsRepository(storage, IosLocale.chosen(), applyLanguage = IosLocale::apply)
    }
    // One of each for the menu and every run, so the menu sees what a run records the moment it
    // records it; see KeyValueStores.
    val stageUnlocks = remember { KeyValueStageUnlockStore(storage) }
    val highscores = remember { KeyValueHighscoreStore(storage) }
    // The pictures go into files of the app's own, too large for its user defaults.
    val achievements = remember { KeyValueAchievementStore(storage, FileScreenshotStorage()) }
    val scope = rememberCoroutineScope()
    val audioSettings = remember(scope) { ObservedAudioSettings(settings, scope) }
    val steeringSettings = remember(scope) { ObservedSteeringSettings(settings, scope) }
    val haptics = remember(scope) { ObservedHaptics(IosHaptics(), settings, scope) }
    // The menu outlives any single run, so it owns its own Audio, and its track is made out here
    // with it rather than with the menu, which leaves composition for every run.
    val menuMusic = remember { IosAudio(sound, assets).newMusic("music/menu.wav") }
    // The menu's view models, kept out here for the same reason.
    val viewModels = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    val backStack = rememberMenuBackStack()
    // Nothing else stops the menu's track when the player leaves the app - the menu only silences
    // it on the way into a run - so it would play on over the home screen until iOS suspends the
    // app. Paused here and brought back on return, as Android's MainActivity does in onStop.
    var resumeMenuMusic by remember { mutableStateOf(false) }
    LaunchedEffect(focus.isFocused) {
        if (!focus.isFocused) {
            resumeMenuMusic = menuMusic.isPlaying
            if (resumeMenuMusic) menuMusic.pause()
        } else if (resumeMenuMusic) {
            resumeMenuMusic = false
            menuMusic.play()
        }
    }
    val stage = playingStage
    val run = rememberRun(
        stage,
        difficulty,
        assets,
        sound,
        controls,
        haptics,
        highscores,
        stageUnlocks,
        achievements,
        audioSettings,
        steeringSettings,
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

            // A run on its way: its text is still being read.
            stage != null -> Box(Modifier.fillMaxSize().background(Color.Black))

            // Edge to edge: each of the menu's screens keeps what is on it clear of the Dynamic
            // Island, the rounded corners and the home indicator itself, and lets its background,
            // the main screen's sky among them, run on under them.
            else -> CyanBatMenu(
                MenuHost(
                    settings = settings,
                    menuMusic = menuMusic,
                    stageUnlocks = stageUnlocks,
                    highscores = highscores,
                    achievements = achievements,
                    // Cleared on the way in as well as on the way out, so that nothing pressed
                    // before the run is waiting to act the moment it starts.
                    onStartGame = { id, chosen ->
                        controls.releaseAll()
                        difficulty = chosen
                        playingStage = id
                    },
                    // An iOS app does not quit itself; the player leaves it as they leave any
                    // other.
                    onExit = null,
                ),
                backStack,
            )
        }
    }
}

/**
 * The run of [stage] on [difficulty], or null while the menu is up or the run's text is still
 * being read: made once it is, and disposed when the player goes back to the menu.
 *
 * @param onExitToMenu takes the player back to the menu.
 */
@Composable
private fun rememberRun(
    stage: Int?,
    difficulty: Difficulty,
    assets: IosAssets,
    sound: IosAudioDevice,
    controls: ControlHandler,
    haptics: Haptics,
    highscores: KeyValueHighscoreStore,
    stageUnlocks: KeyValueStageUnlockStore,
    achievements: KeyValueAchievementStore,
    audioSettings: AudioSettings,
    steeringSettings: SteeringSettings,
    onExitToMenu: () -> Unit,
): ComposeGame? {
    val fonts = LocalFontFamilyResolver.current
    val run by produceState<ComposeGame?>(null, stage, difficulty) {
        value = null
        val id = stage ?: return@produceState
        // Suspending, rather than blocking as the desktop does, so the frame is not held meanwhile.
        val text = GameText.load()
        val game = ComposeGame(
            FRAME_BUFFER_WIDTH,
            FRAME_BUFFER_HEIGHT,
            ComposeGraphics(
                FRAME_BUFFER_WIDTH,
                FRAME_BUFFER_HEIGHT,
                { name -> loadImage(assets, name) },
                fonts,
                ::makeImage,
            ),
            IosAudio(sound, assets),
            controls,
        )
        val env = CyanBatEnvironment(
            assets = GameAssets.load(game.graphics, game.audio),
            text = text,
            haptics = haptics,
            highscores = highscores,
            stageUnlocks = stageUnlocks,
            achievements = achievements,
            onExitToMenu = onExitToMenu,
            audioSettings = audioSettings,
            steeringSettings = steeringSettings,
            // There is no Back on iOS, and no keyboard to count on.
            pauseButton = true,
        )
        game.setScreen(GameScreen(game, env, id, difficulty))
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
private fun loadImage(assets: IosAssets, name: String): ImageBitmap =
    Image.makeFromEncoded(assets.bytes(name)).toComposeImageBitmap()
        .also { it.asSkiaBitmap().setImmutable() }

/**
 * Pixels the game made, as an immutable Skia image like the assets, for the same reason. ARGB ints
 * laid out little-endian are BGRA bytes.
 */
private fun makeImage(width: Int, height: Int, pixels: IntArray): ImageBitmap {
    val bytes = ByteArray(width * height * 4)
    for (i in 0 until width * height) {
        val argb = pixels[i]
        bytes[i * 4] = argb.toByte()
        bytes[i * 4 + 1] = (argb shr 8).toByte()
        bytes[i * 4 + 2] = (argb shr 16).toByte()
        bytes[i * 4 + 3] = (argb ushr 24).toByte()
    }
    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    return Image.makeRaster(info, bytes, width * 4).toComposeImageBitmap()
        .also { it.asSkiaBitmap().setImmutable() }
}
