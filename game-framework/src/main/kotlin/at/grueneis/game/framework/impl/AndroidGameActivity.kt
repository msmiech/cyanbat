package at.grueneis.game.framework.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import at.grueneis.game.framework.Audio
import at.grueneis.game.framework.FileIO
import at.grueneis.game.framework.Game
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input
import at.grueneis.game.framework.Screen
import kotlinx.coroutines.isActive

/**
 * Android Game Framework implementation based on Beginning Android Games.
 *
 * @author Robert Grüneis
 * Modifications by msmiech
 */
abstract class AndroidGameActivity : ComponentActivity(), Game {
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
        val touchHandler = ComposeTouchHandler()

        input = AndroidInput(this, touchHandler)
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

        setContent {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val boxWithConstraintsScope = this

                val scaleX = frameBufferWidth.toFloat() / constraints.maxWidth
                val scaleY = frameBufferHeight.toFloat() / constraints.maxHeight

                var frameTrigger by remember { mutableIntStateOf(0) }

                // High-performance game loop tied directly to the Compose frame callback
                LaunchedEffect(Unit) {
                    var lastTime = System.nanoTime()
                    while (isActive) {
                        withFrameNanos { frameTimeNanos ->
                            val deltaTime = (frameTimeNanos - lastTime) / 1e9f
                            lastTime = frameTimeNanos

                            currentScreen?.update(deltaTime)
                            currentScreen?.present(deltaTime)

                            // Signal Compose that the framebuffer's pixels have been updated
                            frameTrigger++
                        }
                    }
                }

                val imageBitmap = remember(frameBuffer) { frameBuffer.asImageBitmap() }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(scaleX, scaleY) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    touchHandler.onPointerEvent(event, scaleX, scaleY)
                                }
                            }
                        }
                ) {
                    // Read frameTrigger to register a dependency so Compose invalidates and redraws the Canvas
                    // whenever frameTrigger changes (on every frame of the game loop).
                    @Suppress("UNUSED_VARIABLE")
                    val trigger = frameTrigger

                    drawImage(
                        image = imageBitmap,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (useWakeLock && wakeLock != null) {
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT)
        }
        currentScreen?.resume()
    }

    override fun onPause() {
        super.onPause()
        if (useWakeLock) wakeLock?.release()
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
