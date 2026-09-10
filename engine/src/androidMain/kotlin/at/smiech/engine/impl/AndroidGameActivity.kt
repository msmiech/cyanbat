package at.smiech.engine.impl

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
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
import at.smiech.engine.Audio
import at.smiech.engine.Game
import at.smiech.engine.GameButton
import at.smiech.engine.GameLoop
import at.smiech.engine.Graphics
import at.smiech.engine.Input
import at.smiech.engine.Screen
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

    override var currentScreen: Screen? = null
    private var wakeLock: WakeLock? = null
    private val gameLoop = GameLoop(this)

    /**
     * Keyboard, game controller and back-gesture state. Owned by the activity rather than by a
     * screen, because the events it is fed arrive at the window.
     */
    private val controlHandler = ControlHandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Dispose old components if this is a configuration change and we're reusing the Activity?
        // Actually, onCreate is called on a NEW instance usually, but let's be safe if we manage state.
        audio?.dispose()
        
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
        val touchHandler = PointerTouchHandler()

        input = AndroidInput(this, touchHandler, controlHandler)
        graphics = AndroidGraphics(assets, frameBuffer)

        // Back reaches the game as a button rather than finishing the activity, so a screen can
        // give it a meaning - pausing, here. Taken from the dispatcher and not from KEYCODE_BACK
        // because gesture navigation raises no key event at all.
        onBackPressedDispatcher.addCallback(this) {
            controlHandler.onButtonPress(GameButton.BACK)
        }

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

                // Game loop tied directly to the Compose frame callback. The timing rules
                // (including the delta clamp) live in the shared GameLoop so desktop behaves the
                // same way.
                LaunchedEffect(Unit) {
                    while (isActive) {
                        withFrameNanos { frameTimeNanos ->
                            gameLoop.frame(frameTimeNanos)

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
                                    touchHandler.onComposePointerEvent(event, scaleX, scaleY)
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
        gameLoop.reset()
        if (useWakeLock && wakeLock != null) {
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT)
        }
        currentScreen?.resume()
    }

    override fun onPause() {
        super.onPause()
        if (useWakeLock) wakeLock?.release()
        // Whoever has focus now gets the key-up for anything still held, so drop it here rather
        // than come back to a bat flying on a key nobody is pressing.
        controlHandler.releaseAll()
        currentScreen?.pause()
    }

    /**
     * Keyboards and game controllers, both of which Android reports as key codes.
     *
     * Dispatch rather than `onKeyDown`: a controller's events are delivered to the window, and
     * nothing in the Compose tree below holds focus to receive them.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        controlHandler.onAndroidKeyEvent(event) || super.dispatchKeyEvent(event)

    /** A controller's analog sticks, which arrive as motion rather than as keys. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean =
        controlHandler.onAndroidMotionEvent(event) || super.onGenericMotionEvent(event)

    override fun onDestroy() {
        super.onDestroy()
        currentScreen?.dispose()
        currentScreen = null
        audio?.dispose()
    }

    override fun setScreen(screen: Screen) {
        if (screen === currentScreen) return
        currentScreen?.pause()
        currentScreen?.dispose()
        screen.resume()
        screen.update(0f)
        currentScreen = screen
    }

    companion object {
        private const val WAKE_LOCK_TIMEOUT = 512L

        var useWakeLock = false
    }
}
