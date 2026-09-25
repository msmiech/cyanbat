package at.smiech.engine.impl

import at.smiech.engine.Controls
import at.smiech.engine.Input
import at.smiech.engine.Input.TouchEvent

class AndroidInput(
    val touchHandler: PointerTouchHandler,
    override val controls: Controls = Controls.None,
) : Input {

    override fun isTouchDown(pointer: Int): Boolean {
        return touchHandler.isTouchDown(pointer)
    }

    override fun getTouchX(pointer: Int): Int {
        return touchHandler.getTouchX(pointer)
    }

    override fun getTouchY(pointer: Int): Int {
        return touchHandler.getTouchY(pointer)
    }

    // Nothing in the game reads the accelerometer, so it is not switched on. Its listener used to
    // be registered here and never removed, which kept the sensor running - and the activity it
    // was registered with alive - long after the game had closed. Zero, as on desktop.
    override val accelX: Float get() = 0f
    override val accelY: Float get() = 0f
    override val accelZ: Float get() = 0f

    override val touchEvents: List<TouchEvent>
        get() = touchHandler.touchEvents
    override val pointerCount: Int
        get() = touchHandler.pointerCount
}
