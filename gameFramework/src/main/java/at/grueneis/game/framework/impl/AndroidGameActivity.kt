package at.grueneis.game.framework.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.DisplayMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import at.grueneis.game.framework.Audio
import at.grueneis.game.framework.FileIO
import at.grueneis.game.framework.Game
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input
import at.grueneis.game.framework.Screen
import androidx.core.graphics.createBitmap

/**
 * Android Game Framework implementation based on Beginning Android Games
 *
 * @author Robert Grüneis
 *
 *
 * Modifications by msmiech
 */
abstract class AndroidGameActivity : ComponentActivity(), Game {
    private var renderView: AndroidFastRenderView? = null
    override var graphics: Graphics? = null
    override var audio: Audio? = null
    override var input: Input? = null
    override var fileIO: FileIO? = null
    override var currentScreen: Screen? = null
    private var wakeLock: WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT, Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT, Color.TRANSPARENT
            )
        )
        val frameBuffer = createBitmap(frameBufferWidth, frameBufferHeight, Bitmap.Config.RGB_565)
        val displaymetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displaymetrics)
        val displayWidth = displaymetrics.widthPixels
        val displayHeight = displaymetrics.heightPixels
        val scaleX = frameBufferWidth.toFloat() / displayWidth
        val scaleY = frameBufferHeight.toFloat() / displayHeight
        AndroidFastRenderView(this, frameBuffer).also { afrv ->
            setContent {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        afrv
                    }
                )
            }
            renderView = afrv
            input = AndroidInput(this, afrv, scaleX, scaleY)
        }
        graphics = AndroidGraphics(assets, frameBuffer)
        fileIO = AndroidFileIO(assets)
        audio = AndroidAudio(this)

        currentScreen = startScreen
        if (useWakeLock) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "androidgame:wakelock"
            )
        }
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
    }

    override fun setScreen(screen: Screen) {
        currentScreen?.pause()
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
