package at.smiech.cyanbat.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import at.smiech.engine.DisplayMode
import at.smiech.engine.GameLoop
import at.smiech.engine.impl.AmbientBars
import at.smiech.engine.impl.FrameFit
import at.smiech.engine.impl.drawFrameBuffer
import at.smiech.engine.impl.onComposeKeyEvent
import at.smiech.engine.impl.onComposePointerEvent

/**
 * Renders the game's framebuffer and drives it from Compose's frame callback - the desktop
 * counterpart of the Canvas in AndroidGameActivity.
 *
 * @param displayMode how the framebuffer is fitted to the window, as the player set it.
 */
@Composable
fun GameSurface(game: DesktopGame, displayMode: DisplayMode) {
    val loop = remember(game) { GameLoop(game) }

    // Losing focus is desktop's onPause: it pauses the run, for the same reason Android's does -
    // nobody is at the controls. It also drops anything held, because the key-up goes to whoever
    // has focus now and the bat would otherwise fly on a key nobody is pressing.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (!windowFocused) {
            game.controlHandler.releaseAll()
            game.currentScreen?.pause()
        }
    }

    // Claimed as soon as the surface exists, and again whenever the window is handed focus back,
    // because the scene's focus owner does not always survive the round trip.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(game, windowFocused) {
        if (windowFocused) focusRequester.requestFocus()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // A window dragged to another shape is refitted, the same as a phone's screen is.
        val fit = remember(game, displayMode, constraints.maxWidth, constraints.maxHeight) {
            FrameFit.of(
                displayMode,
                game.frameBufferWidth,
                game.frameBufferHeight,
                constraints.maxWidth,
                constraints.maxHeight,
            )
        }
        val ambientBars = remember(game) { AmbientBars() }

        var frameTrigger by remember { mutableIntStateOf(0) }

        LaunchedEffect(game) {
            while (true) {
                withFrameNanos { loop.frame(it) }
                frameTrigger++
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // The game surface owns focus for as long as it is on screen. Without this the
                // scene can sit with no focus owner at all - a mouse click on a Compose button
                // does not take focus - and Compose routes key events through the focus system,
                // so keys reach nobody even though the window is plainly in the foreground.
                .focusRequester(focusRequester)
                .focusable()
                // Handled here rather than left to bubble up to the window, so that the keys the
                // game claims are consumed before Compose can read them as focus traversal. The
                // arrow keys are the ones that matter: unclaimed, they move focus instead of the
                // bat.
                .onKeyEvent(game.controlHandler::onComposeKeyEvent)
                .pointerInput(fit) {
                    awaitPointerEventScope {
                        while (true) {
                            game.touchHandler.onComposePointerEvent(awaitPointerEvent(), fit)
                        }
                    }
                }
        ) {
            // Reading frameTrigger is what makes Compose redraw once per game frame.
            @Suppress("UNUSED_VARIABLE")
            val trigger = frameTrigger

            // Unlike Android's cached, zero-copy Bitmap.asImageBitmap(), this copies each frame.
            // At 480x320 that is cheap enough to prefer over a Skia-backed framebuffer.
            drawFrameBuffer(
                game.frameBuffer.toComposeImageBitmap(),
                fit,
                ambientBars.takeIf { displayMode == DisplayMode.AMBIENT },
            )
        }
    }
}
