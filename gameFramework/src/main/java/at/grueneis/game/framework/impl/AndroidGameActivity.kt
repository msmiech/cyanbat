package at.grueneis.game.framework.impl

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import at.grueneis.game.framework.Audio
import at.grueneis.game.framework.FileIO
import at.grueneis.game.framework.Game
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input
import at.grueneis.game.framework.R
import at.grueneis.game.framework.Screen

/**
 * Android Game Framework implementation based on Beginning Android Games
 *
 * @author Robert Grüneis
 *
 *
 * Modifications by msmiech
 */
abstract class AndroidGameActivity : AppCompatActivity(), Game {
    protected var sharedPrefs: SharedPreferences? = null
    var renderView: AndroidFastRenderView? = null
    override var graphics: Graphics? = null
    override var audio: Audio? = null
    override var input: Input? = null
    override var fileIO: FileIO? = null
    override var currentScreen: Screen? = null
    var wakeLock: WakeLock? = null
    var layoutParams: RelativeLayout.LayoutParams? = null
    var mainLayout: RelativeLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val frameBufferWidth = if (isLandscape) 480 else 320
        val frameBufferHeight = if (isLandscape) 320 else 480
        val frameBuffer = Bitmap.createBitmap(
            frameBufferWidth,
            frameBufferHeight, Bitmap.Config.RGB_565
        )
        val displaymetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displaymetrics)
        val displayWidth = displaymetrics.widthPixels
        val displayHeight = displaymetrics.heightPixels
        val scaleX = frameBufferWidth.toFloat() / displayWidth
        val scaleY = frameBufferHeight.toFloat() / displayHeight
        setContentView(R.layout.main)
        mainLayout = findViewById(R.id.mainLayout)
        layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        renderView = AndroidFastRenderView(this, frameBuffer)
        graphics = AndroidGraphics(assets, frameBuffer)
        fileIO = AndroidFileIO(assets)
        audio = AndroidAudio(this)
        input = AndroidInput(this, renderView!!, scaleX, scaleY)
        mainLayout?.addView(renderView)
        initPreferences()
        currentScreen = startScreen
        if (useWakeLock) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "androidgame:wakelock"
            )
        }
    }

    /**
     * Initialization and preparation of preferences and settings.
     */
    private fun initPreferences() {
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onResume() {
        super.onResume()
        if (useWakeLock && wakeLock != null) {
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT)
        }
        currentScreen?.resume()
        renderView?.resume()
    }

    override fun onPause() {
        super.onPause()
        if (useWakeLock) wakeLock?.release()
        renderView?.pause()
        currentScreen?.pause()
        if (isFinishing) currentScreen?.dispose()
    }

    override fun setScreen(screen: Screen?) {
        requireNotNull(screen) { "Screen is null!" }
        currentScreen!!.pause()
        currentScreen!!.dispose()
        screen.resume()
        screen.update(0f)
        currentScreen = screen
    }

    override val context: Context
        get() = this

    companion object {
        private const val WAKE_LOCK_TIMEOUT = 512L
        var useWakeLock = false
    }
}