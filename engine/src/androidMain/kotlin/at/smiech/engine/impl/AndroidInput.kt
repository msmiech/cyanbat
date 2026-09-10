package at.smiech.engine.impl

import android.content.Context
import at.smiech.engine.Controls
import at.smiech.engine.Input
import at.smiech.engine.Input.TouchEvent

class AndroidInput(
    context: Context,
    val touchHandler: PointerTouchHandler,
    override val controls: Controls = Controls.None,
) : Input {
    var sensorHandler: SensorHandler = SensorHandler(context)

    override fun isTouchDown(pointer: Int): Boolean {
        return touchHandler.isTouchDown(pointer)
    }

    override fun getTouchX(pointer: Int): Int {
        return touchHandler.getTouchX(pointer)
    }

    override fun getTouchY(pointer: Int): Int {
        return touchHandler.getTouchY(pointer)
    }

    override val accelX: Float
        get() = sensorHandler.gravityX
    override val accelY: Float
        get() = sensorHandler.gravityY
    override val accelZ: Float
        get() = sensorHandler.gravityZ
    override val touchEvents: List<TouchEvent>
        get() = touchHandler.touchEvents
    override val pointerCount: Int
        get() = touchHandler.pointerCount
}