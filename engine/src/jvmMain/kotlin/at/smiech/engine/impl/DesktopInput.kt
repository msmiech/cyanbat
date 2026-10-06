package at.smiech.engine.impl

import at.smiech.engine.Controls
import at.smiech.engine.Input

/**
 * [Input] for the desktop. Pointer state comes from the shared [PointerTouchHandler]; keyboard
 * state arrives through [controls].
 */
class DesktopInput(
    val touchHandler: PointerTouchHandler,
    override val controls: Controls = Controls.None,
) : Input {
    override fun isTouchDown(pointer: Int) = touchHandler.isTouchDown(pointer)
    override fun getTouchX(pointer: Int) = touchHandler.getTouchX(pointer)
    override fun getTouchY(pointer: Int) = touchHandler.getTouchY(pointer)

    override val touchEvents: List<Input.TouchEvent> get() = touchHandler.touchEvents
    override val pointerCount: Int get() = touchHandler.pointerCount
}
