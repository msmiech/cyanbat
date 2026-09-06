package at.smiech.engine.impl

import at.smiech.engine.Input

/**
 * [Input] for desktop. Pointer state comes from the shared [PointerTouchHandler]; the
 * accelerometer axes report zero, since no desktop machine has one and nothing in the game
 * reads them.
 */
class DesktopInput(val touchHandler: PointerTouchHandler) : Input {
    override fun isTouchDown(pointer: Int) = touchHandler.isTouchDown(pointer)
    override fun getTouchX(pointer: Int) = touchHandler.getTouchX(pointer)
    override fun getTouchY(pointer: Int) = touchHandler.getTouchY(pointer)

    override val accelX: Float get() = 0f
    override val accelY: Float get() = 0f
    override val accelZ: Float get() = 0f

    override val touchEvents: List<Input.TouchEvent> get() = touchHandler.touchEvents
    override val pointerCount: Int get() = touchHandler.pointerCount
}
