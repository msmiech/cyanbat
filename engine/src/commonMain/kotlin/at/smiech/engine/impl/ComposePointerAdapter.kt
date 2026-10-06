package at.smiech.engine.impl

import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange

/**
 * Feeds Compose pointer changes into the platform-neutral [PointerTouchHandler], mapped from view
 * pixels into the framebuffer through [fit], the same rectangle the framebuffer is drawn into.
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
 * Compose has no flag for it. When Android cancels a touch, as its back gesture does once it claims
 * the swipe, Compose synthesizes an already-consumed release for every pointer that was down.
 * Nothing in the game's canvas consumes a change, so a consumed release is that cancel. Read as a
 * plain release it counted as a tap, and the pause screen resumed on the swipe meant to quit it.
 */
internal val PointerInputChange.isCancel: Boolean
    get() = !pressed && previousPressed && isConsumed
