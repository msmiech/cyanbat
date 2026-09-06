package at.smiech.engine.impl

import androidx.compose.ui.input.pointer.PointerEvent

/**
 * Feeds Compose pointer changes into the platform-neutral [PointerTouchHandler].
 */
fun PointerTouchHandler.onComposePointerEvent(event: PointerEvent, scaleX: Float, scaleY: Float) {
    val changes = event.changes
    pointerCount = changes.count { it.pressed }
    changes.forEach { change ->
        onPointer(
            rawId = change.id.value,
            x = change.position.x,
            y = change.position.y,
            pressed = change.pressed,
            previouslyPressed = change.previousPressed,
            scaleX = scaleX,
            scaleY = scaleY
        )
    }
}
