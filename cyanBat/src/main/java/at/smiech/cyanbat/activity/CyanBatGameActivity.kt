package at.smiech.cyanbat.activity

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import at.grueneis.game.framework.Graphics.PixmapFormat
import at.grueneis.game.framework.Screen
import at.grueneis.game.framework.impl.AndroidGameActivity
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.ui.GameScreen
import at.smiech.cyanbat.util.DEBUG
import at.smiech.cyanbat.util.TAG

class CyanBatGameActivity : AndroidGameActivity() {

    override val startScreen: Screen
        get() {
            if (DEBUG)
                Log.d(TAG, "getStartScreen")
            initAssets()
            return GameScreen(this)
        }

    private fun initAssets() {
        if (DEBUG)
            Log.d(TAG, "initAssets")

        // Loading image assets
        val graphicsAssets = graphics?.let { g ->
            GameAssets.Graphics(
                bat = g.newPixmap("cyanBat.png", PixmapFormat.ARGB8888),
                gameOver = g.newPixmap("gameover.png", PixmapFormat.ARGB8888),
                background = g.newPixmap("background.jpg", PixmapFormat.ARGB8888),
                topObstacles = arrayOf(
                    g.newPixmap("topObstacle1.png", PixmapFormat.ARGB8888),
                    g.newPixmap("topObstacle2.png", PixmapFormat.ARGB8888)
                ),
                bottomObstacles = arrayOf(
                    g.newPixmap(
                        "bottomObstacle1.png",
                        PixmapFormat.ARGB8888
                    ),
                    g.newPixmap(
                        "bottomObstacle2.png",
                        PixmapFormat.ARGB8888
                    )
                ),
                death = g.newPixmap(
                    "death.png", PixmapFormat.ARGB8888
                ),
                enemy = g.newPixmap(
                    "enemies.png", PixmapFormat.ARGB8888,
                ),
                explosion = g.newPixmap("explosion.png", PixmapFormat.ARGB8888),
                shot = g.newPixmap("shot.png", PixmapFormat.ARGB8888)
            )
        } ?: throw IllegalStateException("Graphics not initialized")

        musicEnabled = true

        // music setup
        val audioAssets = audio?.let {
            GameAssets.Audio(
                gameTrack = it.newMusic("game_theme.mp3"),
                gameOverMusic = it.newMusic("game_over.mp3"),
                deathSound = it.newSound("deathSound.mp3")
            )
        } ?: throw IllegalStateException("Audio not initialized")

        soundsEnabled = true

        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        gameAssets = GameAssets(graphicsAssets, audioAssets, vib)
    }


    override fun onResume() {
        super.onResume()
        // checking pref changes
        musicEnabled = true
        soundsEnabled = true
    }

    companion object {
        lateinit var gameAssets: GameAssets
        var musicEnabled = true
        var soundsEnabled = true
    }
}
