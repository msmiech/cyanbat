package at.smiech.cyanbat.activities

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import at.grueneis.game.framework.Graphics.PixmapFormat
import at.grueneis.game.framework.Music
import at.grueneis.game.framework.Pixmap
import at.grueneis.game.framework.Screen
import at.grueneis.game.framework.Sound
import at.grueneis.game.framework.code.AndroidGameActivity
import at.smiech.cyanbat.screens.GameScreen
import at.smiech.cyanbat.util.MusicPlayer

class CyanBatGameActivity : AndroidGameActivity() {

    override val startScreen: Screen
        get() {
            if (DEBUG)
                Log.d(TAG, "getStartScreen")
            currentActivity = this
            initAssets()
            return GameScreen(this)
        }

    private fun initAssets() {
        if (DEBUG)
            Log.d(TAG, "initAssets")

        val g = graphics!!
        // Loading image assets
        bat = g.newPixmap("cyanBat.png", PixmapFormat.ARGB4444)
        gameOver = g.newPixmap("gameover.png", PixmapFormat.ARGB4444)
        background = g.newPixmap("background.jpg", PixmapFormat.ARGB4444)
        topObstacles[0] = g
                .newPixmap("topObstacle1.png", PixmapFormat.ARGB4444)
        topObstacles[1] = g
                .newPixmap("topObstacle2.png", PixmapFormat.ARGB4444)
        bottomObstacles[0] = g.newPixmap("bottomObstacle1.png",
                PixmapFormat.ARGB4444)
        bottomObstacles[1] = g.newPixmap("bottomObstacle2.png",
                PixmapFormat.ARGB4444)
        death = g.newPixmap("death.png", PixmapFormat.ARGB4444)
        enemies = g.newPixmap("enemies.png", PixmapFormat.ARGB4444)
        explosion = g.newPixmap("explosion.png", PixmapFormat.ARGB4444)
        shot = g.newPixmap("shot.png", PixmapFormat.ARGB4444)

        musicPlayer = MusicPlayer()
        musicEnabled = this.sharedPrefs!!.getBoolean("music_enabled", true)
        musicPlayer.setEnabled(musicEnabled)

        //music
        gameTrack = audio!!.newMusic("game_theme.mp3")
        gameOverMusic = audio!!.newMusic("game_over.mp3")
        //sound
        soundsEnabled = this.sharedPrefs!!.getBoolean("sounds_enabled", true)
        deathSound = audio!!.newSound("deathSound.mp3")

        vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window = window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.navigationBarColor = Color.BLACK
        }

        super.onCreate(savedInstanceState, persistentState)
    }


    override fun onResume() {
        //checking changes prefs
        musicEnabled = this.sharedPrefs!!.getBoolean("music_enabled", true)
        musicPlayer.setEnabled(musicEnabled)
        soundsEnabled = this.sharedPrefs!!.getBoolean("sounds_enabled", true)
        super.onResume()
    }

    companion object {

        val TAG = "CyanBatGameActivity"
        val DEBUG = false
        val SHOOTING_ENABLED = false

        //Legacy code... public static for simplicity
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
        var currentActivity: CyanBatGameActivity? = null
        var musicEnabled = true
        var soundsEnabled = true
    }
}
