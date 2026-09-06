package at.smiech.cyanbat.desktop

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import at.smiech.engine.GameLoop
import at.smiech.engine.impl.onComposePointerEvent

/**
 * Renders the game's framebuffer and drives it from Compose's frame callback - the desktop
 * counterpart of the Canvas in AndroidGameActivity.
 */
@Composable
fun GameSurface(game: DesktopGame) {
    val loop = remember(game) { GameLoop(game) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scaleX = game.frameBufferWidth.toFloat() / constraints.maxWidth
        val scaleY = game.frameBufferHeight.toFloat() / constraints.maxHeight

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
                .pointerInput(scaleX, scaleY) {
                    awaitPointerEventScope {
                        while (true) {
                            game.touchHandler.onComposePointerEvent(
                                awaitPointerEvent(), scaleX, scaleY
                            )
                        }
                    }
                }
        ) {
            // Reading frameTrigger is what makes Compose redraw once per game frame.
            @Suppress("UNUSED_VARIABLE")
            val trigger = frameTrigger

            // Unlike Android's cached, zero-copy Bitmap.asImageBitmap(), this copies each frame.
            // At 480x320 that is cheap enough to prefer over a Skia-backed framebuffer.
            drawImage(
                image = game.frameBuffer.toComposeImageBitmap(),
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
        }
    }
}
