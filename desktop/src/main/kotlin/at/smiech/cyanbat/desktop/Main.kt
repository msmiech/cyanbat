package at.smiech.cyanbat.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.singleWindowApplication
import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.data.ObservedAudioSettings
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.Level
import at.smiech.cyanbat.ui.CyanBatMenu
import at.smiech.cyanbat.ui.MenuHost
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.engine.Graphics.PixmapFormat
import at.smiech.engine.Haptics
import at.smiech.engine.impl.DesktopAudio
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.system.exitProcess

private const val FRAME_BUFFER_WIDTH = 480
private const val FRAME_BUFFER_HEIGHT = 320

fun main() = singleWindowApplication(title = "CyanBat") {
    CyanBatApp()
}

/**
 * Desktop shell: the shared menu, and the game when it is running.
 *
 * Android splits these across two Activities; on desktop one window swaps its content, which is
 * why "back to menu" here is a state change rather than an Intent.
 */
@Composable
private fun CyanBatApp() {
    var playing by remember { mutableStateOf(false) }
    val settings = remember { PreferencesSettingsRepository() }
    val scope = rememberCoroutineScope()
    val audioSettings = remember(scope) { ObservedAudioSettings(settings, scope) }
    // The menu outlives any single game instance, so it owns its own Audio.
    val menuAudio = remember { DesktopAudio { name ->
        object {}.javaClass.getResourceAsStream("/$name") ?: error("Asset <$name> not found")
    } }

    if (playing) {
        val game = remember {
            DesktopGame(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT).also { game ->
                val env = CyanBatEnvironment(
                    assets = loadAssets(game),
                    haptics = Haptics.None,
                    highscores = PreferencesHighscoreStore(),
                    onExitToMenu = { playing = false },
                    audioSettings = audioSettings,
                )
                game.setScreen(GameScreen(game, env))
            }
        }
        GameSurface(game)
    } else {
        CyanBatMenu(
            MenuHost(
                settings = settings,
                menuMusic = remember { menuAudio.newMusic("menu_theme.mp3") },
                onStartGame = { playing = true },
                onExit = { exitProcess(0) },
            )
        )
    }
}

/** Desktop counterpart of CyanBatGameActivity.loadAssets. */
private fun loadAssets(game: DesktopGame): GameAssets {
    val g = game.graphics
    val a = game.audio
    return GameAssets(
        graphics = GameAssets.Graphics(
            bat = g.newPixmap("cyanBat.png", PixmapFormat.ARGB8888),
            gameOver = g.newPixmap("gameover.png", PixmapFormat.ARGB8888),
            death = g.newPixmap("death.png", PixmapFormat.ARGB8888),
            enemy = g.newPixmap("enemies.png", PixmapFormat.ARGB8888),
            explosion = g.newPixmap("explosion.png", PixmapFormat.ARGB8888),
            shot = g.newPixmap("shot.png", PixmapFormat.ARGB8888),
        ),
        audio = GameAssets.Audio(
            gameOverMusic = a.newMusic("game_over.mp3"),
            deathSound = a.newSound("deathSound.mp3"),
        ),
        levels = listOf(
            Level(
                id = 1,
                name = "Level 1: The Cave",
                background = g.newPixmap("background.jpg", PixmapFormat.ARGB8888),
                topObstacles = arrayOf(
                    g.newPixmap("topObstacle1.png", PixmapFormat.ARGB8888),
                    g.newPixmap("topObstacle2.png", PixmapFormat.ARGB8888),
                ),
                bottomObstacles = arrayOf(
                    g.newPixmap("bottomObstacle1.png", PixmapFormat.ARGB8888),
                    g.newPixmap("bottomObstacle2.png", PixmapFormat.ARGB8888),
                ),
                music = a.newMusic("game_theme.mp3"),
            ),
        ),
    )
}
