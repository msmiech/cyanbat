package at.smiech.engine.impl

import androidx.compose.ui.input.pointer.PointerEvent

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
        )
    }
}
