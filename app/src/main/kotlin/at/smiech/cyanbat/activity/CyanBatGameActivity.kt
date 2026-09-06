package at.smiech.cyanbat.activity

import android.content.Intent
import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.MainActivity
import at.smiech.cyanbat.dataStore
import at.smiech.cyanbat.data.DataStoreHighscoreStore
import at.smiech.cyanbat.data.DataStoreSettingsRepository
import at.smiech.cyanbat.data.ObservedAudioSettings
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.Level
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.engine.Graphics.PixmapFormat
import at.smiech.engine.Screen
import at.smiech.engine.impl.AndroidGameActivity
import at.smiech.engine.impl.AndroidHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Android host for the game. Its whole job is to build a [CyanBatEnvironment] out of platform
 * pieces and hand it to the shared [GameScreen]; the desktop entry point does the same with its
 * own pieces.
 */
class CyanBatGameActivity : AndroidGameActivity() {
    override val startScreen: Screen
        get() = GameScreen(this, buildEnvironment())

    /** Feeds the live audio settings; cancelled with the activity. */
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val frameBufferWidth: Int get() = 480
    override val frameBufferHeight: Int get() = 320

    private fun buildEnvironment(): CyanBatEnvironment = CyanBatEnvironment(
        assets = loadAssets(),
        haptics = AndroidHaptics(this),
        highscores = DataStoreHighscoreStore(dataStore),
        onExitToMenu = {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        },
        audioSettings = ObservedAudioSettings(DataStoreSettingsRepository(dataStore), activityScope),
    )

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun loadAssets(): GameAssets {
        val g = graphics ?: error("Graphics not initialized")
        val a = audio ?: error("Audio not initialized")

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
}
