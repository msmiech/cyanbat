package at.grueneis.game.framework.impl

import android.content.Context
import android.view.View
import at.grueneis.game.framework.Input
import at.grueneis.game.framework.Input.TouchEvent

class AndroidInput(context: Context, view: View, scaleX: Float, scaleY: Float) : Input {
    var sensorHandler: SensorHandler = SensorHandler(context)
    var touchHandler: TouchHandler = MultiTouchHandler(view, scaleX, scaleY)
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
    override val touchEvents: List<TouchEvent>?
        get() = touchHandler.touchEvents
    override val pointerCount: Int
        get() = touchHandler.pointerCount

    init {
        //keyHandler = new KeyboardHandler(view);
        //touchHandler = new SingleTouchHandler(view, scaleX, scaleY);
    }
}