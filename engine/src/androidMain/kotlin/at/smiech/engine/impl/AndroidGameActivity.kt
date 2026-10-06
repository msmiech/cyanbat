package at.smiech.engine.impl

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
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
 * The Android host: an activity that runs the shared [GameLoop] on Compose's frame clock, draws
 * each frame into a Compose canvas and feeds touch, keys and game controllers to the game. A
 * subclass supplies the [startScreen].
 *
 * Originally based on the framework from *Beginning Android Games*.
 *
 * @author Robert Grüneis
 * @author msmiech
 */
abstract class AndroidGameActivity : ComponentActivity(), Game {
    override var graphics: Graphics? = null

    /** [graphics], as the frame is drawn from. */
    private lateinit var frame: ComposeGraphics
    override var audio: Audio? = null
    override var input: Input? = null

    override var currentScreen: Screen? = null
    private val gameLoop = GameLoop(this, trace = AndroidFrameTrace)

    /** The screen shown when the activity is created. */
    protected abstract val startScreen: Screen

    /**
     * Keyboard, game controller and back-gesture state. Owned by the activity rather than a screen,
     * because those events arrive at the window.
     */
    private val controlHandler = ControlHandler()

    /**
     * The input device the player last used through a key or a stick, or null once they touch the
     * screen; this is what [haptics] rumbles. Only touched on the main thread.
     */
    private var controllerInUse: Int? = null

    /**
     * Vibration for the host to hand its screens, routed to whatever the player is holding; see
     * [AndroidHaptics]. Lazy, because it needs the activity's context.
     */
    protected val haptics: Haptics by lazy { AndroidHaptics(this) { controllerInUse } }

    /**
     * How the framebuffer is fitted to the screen, for a host that lets the player choose; null
     * means [DisplayMode.DEFAULT]. Collected while the game is shown, so a change applies on the
     * next frame.
     */
    protected open val displayModes: Flow<DisplayMode>? = null

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
        // decide what it means. Taken from the dispatcher rather than KEYCODE_BACK because gesture
        // navigation raises no key event.
        onBackPressedDispatcher.addCallback(this) {
            controlHandler.onButtonPress(GameButton.BACK)
        }

        audio = AndroidAudio(this)
        currentScreen = startScreen

        setContent {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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

                // The game loop runs on Compose's frame clock. Its timing rules, the delta clamp
                // included, live in the shared GameLoop so the desktop behaves the same.
                LaunchedEffect(Unit) {
                    while (isActive) {
                        withFrameNanos { frameTimeNanos ->
                            gameLoop.frame(frameTimeNanos)

                            // Tells Compose a new frame has been recorded to draw.
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
                    // Reading frameTrigger makes the canvas redraw on every frame of the game loop.
                    @Suppress("UNUSED_VARIABLE")
                    val trigger = frameTrigger

                    // A trace section of its own, after the loop's update and present: the cost to
                    // the main thread of handing the frame to HWUI. The GPU's work shows up on the
                    // RenderThread.
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
            ?: error("Could not decode the image asset $filename")
        bitmap.prepareToDraw()
        return bitmap.asImageBitmap()
    }

    override fun onResume() {
        super.onResume()
        gameLoop.reset()
        currentScreen?.resume()
    }

    override fun onPause() {
        super.onPause()
        // The key-up for anything still held now goes to whoever has focus, so release it here
        // rather than come back to a bat flying on a key nobody is pressing.
        controlHandler.releaseAll()
        currentScreen?.pause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Before Android 11 a hidden status bar is a view flag that the system clears when the
        // player leaves the window, so hide it again whenever focus returns.
        if (hasFocus) hideStatusBar()
    }

    /**
     * Hides the status bar during play, because its clock and icons covered the HUD's top row. A
     * swipe down from the top edge shows it briefly. The navigation bar stays, because Back pauses.
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
     * nothing in the Compose tree holds focus to receive them.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!controlHandler.onAndroidKeyEvent(event)) return super.dispatchKeyEvent(event)
        controllerInUse = event.deviceId
        return true
    }

    /** A controller's analog sticks, which arrive as motion rather than as keys. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!controlHandler.onAndroidMotionEvent(event)) return super.onGenericMotionEvent(event)
        // Only a stick pushed past its dead zone counts: a pad left on the table reports a drifting
        // stick too.
        if (controlHandler.moveX != 0f || controlHandler.moveY != 0f) controllerInUse =
            event.deviceId
        return true
    }

    /** A finger on the screen hands vibration back to the phone. */
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
}
