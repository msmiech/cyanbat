package at.smiech.cyanbat.activities

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import at.grueneis.game.framework.Graphics.PixmapFormat
import at.grueneis.game.framework.Music
import at.grueneis.game.framework.Pixmap
import at.grueneis.game.framework.Screen
import at.grueneis.game.framework.Sound
import at.grueneis.game.framework.impl.AndroidGameActivity
import at.smiech.cyanbat.screen.GameScreen
import at.smiech.cyanbat.util.MusicPlayer

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
        graphics?.let { g ->
            bat = g.newPixmap("cyanBat.png", PixmapFormat.ARGB8888)
            gameOver = g.newPixmap("gameover.png", PixmapFormat.ARGB8888)
            background = g.newPixmap("background.jpg", PixmapFormat.ARGB8888)
            topObstacles[0] = g
                .newPixmap("topObstacle1.png", PixmapFormat.ARGB8888)
            topObstacles[1] = g
                .newPixmap("topObstacle2.png", PixmapFormat.ARGB8888)
            bottomObstacles[0] = g.newPixmap("bottomObstacle1.png",
                PixmapFormat.ARGB8888)
            bottomObstacles[1] = g.newPixmap("bottomObstacle2.png",
                PixmapFormat.ARGB8888)
            death = g.newPixmap("death.png", PixmapFormat.ARGB8888)
            enemies = g.newPixmap("enemies.png", PixmapFormat.ARGB8888)
            explosion = g.newPixmap("explosion.png", PixmapFormat.ARGB8888)
            shot = g.newPixmap("shot.png", PixmapFormat.ARGB8888)
        }

        musicPlayer = MusicPlayer()
        musicEnabled = sharedPrefs?.getBoolean("music_enabled", true) ?: true
        musicPlayer.setEnabled(musicEnabled)

        //music
        audio?.let {
            gameTrack = it.newMusic("game_theme.mp3")
            gameOverMusic = it.newMusic("game_over.mp3")
            deathSound = it.newSound("deathSound.mp3")
        }

        //sound
        soundsEnabled = sharedPrefs?.getBoolean("sounds_enabled", true) ?: true

        vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }


    }

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.navigationBarColor = Color.BLACK

        super.onCreate(savedInstanceState, persistentState)
    }


    override fun onResume() {
        // checking pref changes
        musicEnabled = this.sharedPrefs?.getBoolean("music_enabled", true) == true
        musicPlayer.setEnabled(musicEnabled)
        soundsEnabled = this.sharedPrefs?.getBoolean("sounds_enabled", true) == true
        super.onResume()
    }

    companion object {
        const val TAG = "CyanBatGameActivity"
        const val DEBUG = false
        const val SHOOTING_ENABLED = false

        lateinit var bat: Pixmap
        lateinit var death: Pixmap
        lateinit var gameOver: Pixmap
        lateinit var background: Pixmap
        var topObstacles = arrayOfNulls<Pixmap>(2)
        var bottomObstacles = arrayOfNulls<Pixmap>(2)
        lateinit var gameTrack: Music
        lateinit var deathSound: Sound
        lateinit var gameOverMusic: Music
        lateinit var enemies: Pixmap
        lateinit var vib: Vibrator
        lateinit var musicPlayer: MusicPlayer
        lateinit var explosion: Pixmap
        lateinit var shot: Pixmap
        var musicEnabled = true
        var soundsEnabled = true
    }
}
