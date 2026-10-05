package at.smiech.engine.impl

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import at.smiech.engine.Audio
import at.smiech.engine.DisplayMode
import at.smiech.engine.Game
import at.smiech.engine.GameButton
import at.smiech.engine.GameLoop
import at.smiech.engine.Graphics
import at.smiech.engine.Haptics
import at.smiech.engine.Input
import at.smiech.engine.Screen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive

/**
 * Android Game Framework implementation based on Beginning Android Games.
 *
 * @author Robert Grüneis
 * Modifications by msmiech
 */
abstract class AndroidGameActivity : ComponentActivity(), Game {
    override var graphics: Graphics? = null

    /** [graphics], as what the frame is drawn from. */
    private lateinit var frame: ComposeGraphics
    override var audio: Audio? = null
    override var input: Input? = null

    override var currentScreen: Screen? = null
    private var wakeLock: WakeLock? = null
    private val gameLoop = GameLoop(this, trace = AndroidFrameTrace)

    /**
     * Keyboard, game controller and back-gesture state. Owned by the activity rather than by a
     * screen, because the events it is fed arrive at the window.
     */
    private val controlHandler = ControlHandler()

    /**
     * The input device the player last played with through a key or a stick, or null once they
     * touch the screen: what [haptics] rumbles. Written on the main thread, where the game loop
     * that vibrates also runs.
     */
    private var controllerInUse: Int? = null

    /**
     * Vibration for the host to hand its screens, routed to whatever the player is holding; see
     * [AndroidHaptics]. Lazily, because it needs the activity's context, which a field initializer
     * lacks.
     */
    protected val haptics: Haptics by lazy { AndroidHaptics(this) { controllerInUse } }

    /**
     * How the framebuffer is fitted to the screen, for a host that lets the player choose; null
     * shows [DisplayMode.DEFAULT]. Collected while the game is on screen, so a new value takes
     * effect on the next frame.
     */
    protected open val displayModes: Flow<DisplayMode>? = null

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
        hideStatusBar()

        val touchHandler = PointerTouchHandler()

        input = AndroidInput(touchHandler, controlHandler)
        frame = ComposeGraphics(
            frameBufferWidth,
            frameBufferHeight,
            ::loadImage,
            createFontFamilyResolver(this),
        )
        graphics = frame

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

                val displayMode by remember { displayModes ?: flowOf(DisplayMode.DEFAULT) }
                    .collectAsState(DisplayMode.DEFAULT)
                val fit = remember(displayMode, constraints.maxWidth, constraints.maxHeight) {
                    FrameFit.of(
                        displayMode,
                        frameBufferWidth,
                        frameBufferHeight,
                        constraints.maxWidth,
                        constraints.maxHeight,
                    )
                }
                val ambientBars = remember { AmbientBars() }

                var frameTrigger by remember { mutableIntStateOf(0) }

                // Game loop tied directly to the Compose frame callback. The timing rules
                // (including the delta clamp) live in the shared GameLoop so desktop behaves the
                // same way.
                LaunchedEffect(Unit) {
                    while (isActive) {
                        withFrameNanos { frameTimeNanos ->
                            gameLoop.frame(frameTimeNanos)

                            // Signal Compose that a new frame has been recorded to draw.
                            frameTrigger++
                        }
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(fit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    touchHandler.onComposePointerEvent(event, fit)
                                }
                            }
                        }
                ) {
                    // Read frameTrigger to register a dependency so Compose invalidates and redraws the Canvas
                    // whenever frameTrigger changes (on every frame of the game loop).
                    @Suppress("UNUSED_VARIABLE")
                    val trigger = frameTrigger

                    // Its own section in the trace, after the loop's update and present: this is where
                    // the frame is handed to HWUI, and what that costs the main thread. The GPU's own
                    // work shows up on the RenderThread.
                    AndroidFrameTrace.begin(ComposeGraphics.DRAW_SECTION)
                    try {
                        drawGameFrame(
                            frame,
                            fit,
                            ambientBars.takeIf { displayMode == DisplayMode.AMBIENT })
                    } finally {
                        AndroidFrameTrace.end()
                    }
                }
            }
        }
    }

    /**
     * An asset decoded for drawing. Immutable, as BitmapFactory leaves it, so HWUI can keep it on the
     * GPU from the first frame that draws it; `prepareToDraw` starts that upload early.
     */
    private fun loadImage(filename: String): ImageBitmap {
        val bitmap = assets.open(filename).use { BitmapFactory.decodeStream(it) }
            ?: throw RuntimeException("Asset-Bitmap <$filename> not found!")
        bitmap.prepareToDraw()
        return bitmap.asImageBitmap()
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Before Android 11 a hidden status bar is a view flag, and the system clears it when the
        // player leaves the window, so hide it again whenever focus comes back.
        if (hasFocus) hideStatusBar()
    }

    /**
     * Hides the status bar for the whole run, because its clock and icons sat over the HUD's top
     * row. A swipe down from the top edge shows it for a moment, and then it hides again by
     * itself. The navigation bar stays, because Back is how the player pauses.
     */
    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    /**
     * Keyboards and game controllers, both of which Android reports as key codes.
     *
     * Dispatch rather than `onKeyDown`: a controller's events are delivered to the window, and
     * nothing in the Compose tree below holds focus to receive them.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!controlHandler.onAndroidKeyEvent(event)) return super.dispatchKeyEvent(event)
        controllerInUse = event.deviceId
        return true
    }

    /** A controller's analog sticks, which arrive as motion rather than as keys. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!controlHandler.onAndroidMotionEvent(event)) return super.onGenericMotionEvent(event)
        // Only a stick pushed past its dead zone. A pad left on the table reports a drifting stick
        // too, and that is not the player picking it up.
        if (controlHandler.moveX != 0f || controlHandler.moveY != 0f) controllerInUse =
            event.deviceId
        return true
    }

    /** A finger on the screen hands vibration back to the phone, which is then in the player's hands. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) controllerInUse = null
        return super.dispatchTouchEvent(event)
    }

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
