package at.smiech.cyanbat.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.singleWindowApplication
import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.Level
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.engine.GameLoop
import at.smiech.engine.Graphics.PixmapFormat
import at.smiech.engine.Haptics
import at.smiech.engine.impl.onComposePointerEvent
import kotlin.system.exitProcess

private const val FRAME_BUFFER_WIDTH = 480
private const val FRAME_BUFFER_HEIGHT = 320

fun main() {
    val game = DesktopGame(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT)
    val env = CyanBatEnvironment(
        assets = loadAssets(game),
        haptics = Haptics.None,
        highscores = PreferencesHighscoreStore(),
        onExitToMenu = { exitProcess(0) },
    )
    game.setScreen(GameScreen(game, env))

    val loop = GameLoop(game)

    singleWindowApplication(title = "CyanBat") {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val scaleX = FRAME_BUFFER_WIDTH.toFloat() / constraints.maxWidth
            val scaleY = FRAME_BUFFER_HEIGHT.toFloat() / constraints.maxHeight

            var frameTrigger by remember { mutableIntStateOf(0) }

            LaunchedEffect(Unit) {
                while (true) {
                    withFrameNanos { loop.frame(it) }
                    frameTrigger++
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(scaleX, scaleY) {
                        awaitPointerEventScope {
                            while (true) {
                                game.touchHandler.onComposePointerEvent(
                                    awaitPointerEvent(), scaleX, scaleY
                                )
                            }
                        }
                    }
            ) {
                // Reading frameTrigger makes Compose redraw once per game frame.
                @Suppress("UNUSED_VARIABLE")
                val trigger = frameTrigger

                // Unlike Android's cached, zero-copy Bitmap.asImageBitmap(), this copies each
                // frame. At 480x320 that is cheap enough to prefer over a Skia-backed framebuffer.
                drawImage(
                    image = game.frameBuffer.toComposeImageBitmap(),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }
        }
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
