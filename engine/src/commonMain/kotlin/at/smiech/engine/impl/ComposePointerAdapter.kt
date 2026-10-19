package at.smiech.engine.impl

import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import at.smiech.engine.InputMethod

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

/**
 * Tells [ControlHandler] the player is using the pointer, when [event] is one they made: a press,
 * or a move that went somewhere. A touch is [InputMethod.TOUCH], a mouse
 * [InputMethod.KEYBOARD_MOUSE].
 *
 * A move that went nowhere is left out, because Compose makes one up whenever what is under a
 * resting mouse changes, as it does when a run starts; read as the player's, it took the prompts
 * off the controller in their hands.
 */
fun ControlHandler.onComposePointerUse(event: PointerEvent) {
    val change = event.changes.firstOrNull() ?: return
    val used = when (event.type) {
        PointerEventType.Press -> true
        PointerEventType.Move -> change.position != change.previousPosition
        else -> false
    }
    if (!used) return
    use(if (change.type == PointerType.Mouse) InputMethod.KEYBOARD_MOUSE else InputMethod.TOUCH)
}
