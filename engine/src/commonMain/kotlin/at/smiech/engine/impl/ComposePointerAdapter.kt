package at.smiech.engine.impl

import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange

/**
 * Feeds Compose pointer changes into the platform-neutral [PointerTouchHandler], mapped from view
 * pixels into the framebuffer through [fit] - the same rectangle the framebuffer is drawn into.
 */
fun PointerTouchHandler.onComposePointerEvent(event: PointerEvent, fit: FrameFit) {
    val changes = event.changes
    pointerCount = changes.count { it.pressed }
    changes.forEach { change ->
        onPointer(
            rawId = change.id.value,
            x = fit.toFrameBufferX(change.position.x),
            y = fit.toFrameBufferY(change.position.y),
            pressed = change.pressed,
            previouslyPressed = change.previousPressed,
            canceled = change.isCancel,
        )
    }
}

/**
 * Whether this change is the system taking the pointer away rather than the finger lifting.
 *
 * Compose carries no flag for that. When Android cancels a touch - its back gesture does, once it
 * recognizes the swipe as its own - Compose makes up a release for every pointer that was down,
 * already consumed. Nothing in the game's canvas consumes a change, so a release that arrives
 * consumed is that cancel. Read as a plain release, it was a tap: the pause screen resumed on the
 * very swipe that was meant to quit it.
 */
internal val PointerInputChange.isCancel: Boolean
    get() = !pressed && previousPressed && isConsumed
